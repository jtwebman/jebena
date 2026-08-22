# Jebena concurrency: M:N green threads (BEAM-inspired)

Target: **M:N green threads (cooperative fibers) balanced across a pool of OS
carrier threads — one carrier per CPU.** Millions of cheap fibers AND real
multicore parallelism. This is Loom's / BEAM's model, adapted to Java's
shared-heap reality. NOT raw 1:1 OS threads.

## Why green threads, not OS threads

- Our GC **moves** objects (mark-compact major + copying young). With raw OS
  threads, any thread can hold a raw heap pointer while another triggers a
  relocating collection → corruption. Green threads let us collect only when
  every fiber is parked at a known safe point.
- A fiber costs ~KB (a saved interpreter stack) vs ~MB per OS thread → millions
  of fibers, i.e. far more requests per machine.

## What we take from BEAM, and where Java differs

| BEAM technique | Jebena |
| --- | --- |
| Reduction-counting preemption (budget of work per process, then yield) | **Adopt.** We already have a per-run `Budget`; a fiber gets a step budget and yields at zero. |
| Per-core schedulers, run-queues, work-stealing | **Adopt** (the M:N carriers). |
| Per-process heaps (share-nothing) → independent per-process GC, no global stop-the-world | **Cannot fully adopt** — but can be *approximated*. See "Per-fiber nurseries" below. |

### Per-fiber heaps: why not purely, and the hybrid that works

A *pure* BEAM per-process heap breaks Java. BEAM works because Erlang is
share-nothing: processes communicate only by *copying* immutable messages, so no
mutable reference is ever shared. Java is the opposite — static fields,
singletons, `ConcurrentHashMap`, interned strings, `Class` objects all hold
objects that multiple threads read **and mutate** and must all see. Private
per-fiber heaps would leave one fiber pointing into another's heap (dangles when
that heap moves), and copying-on-share breaks the "same object, shared writes"
contract because Java sharing is implicit (any field write).

The hybrid — most of the benefit, preserves Java semantics, and fits our
existing generational GC (write barrier + remembered set):

- Each fiber/carrier gets a private **nursery (young region)**; allocation there
  is lock-free (TLAB).
- Most objects are born and die in the nursery → collect it **independently,
  without stopping other fibers**. This is the BEAM-like win for the common case.
- The **write barrier detects escape** — when a young object becomes reachable
  from outside the fiber, promote it to the **shared old generation** (global,
  safepointed). Only genuinely-shared objects pay global-coordination cost.

So: per-fiber **stacks** — always (mandatory, free). Per-fiber **heaps** — as
private nurseries over a shared old gen, not as full share-nothing heaps.

The win of being a VM: **safepoints are cheap.** "Stop the world" = each carrier
parks at its next yield point (allocation, backward branch, method entry) — the
same yield points the scheduler uses. Native OS-thread code can't be halted that
cleanly. Coordination scales with CPU count (~8), not thread count (~millions).

## The prerequisite: a resumable interpreter

Today `exec` uses **native recursion** (`exec` calls `exec` for each invoke).
You cannot suspend a fiber blocked deep in a call chain that way. So the
foundation is an **explicit-stack, resumable interpreter**: a fiber owns an
explicit stack of call frames; the dispatch loop runs the top frame; invoke
pushes a frame, return pops one. Suspending a fiber is then trivial — its whole
state is already heap data. Bonus: GC roots become **precise and cheap** to
enumerate (walk each fiber's frame list).

```
Fiber { frames: [CallFrame], status, reductions_left, ... }
CallFrame { class, code, pc, locals[], operand_stack[], sp, exc_table }

run(fiber):
  loop:
    frame = fiber.frames.top
    op = frame.code[frame.pc]
    reductions_left -= 1
    if reductions_left == 0: return .yielded        # scheduler reschedules
    switch op:
      invoke*: push new CallFrame                    # no Zig recursion
      *return: pop CallFrame, push result to caller
      (alloc / backward-branch): safepoint poll      # GC / stop-the-world
```

## Staged plan (one loop iteration per stage, each gated green)

1. **Explicit-stack resumable interpreter** — pure refactor of `exec` from native
   recursion to an explicit frame stack. Same semantics, zero behavior change;
   every differential test stays green. (Native re-entry — reflectInvoke,
   Method.invoke, getEnumConstants — may keep a nested loop for now; matches real
   JVMs which also can't cheaply suspend in native code.)
2. **Fiber object + reduction-counting yield points** — wrap execution in a Fiber
   with a step budget; yield at zero and on blocking ops. Still one scheduler,
   run-to-completion by default (deterministic).
3. **Cooperative scheduler + run-queue** — `Thread.start()` enqueues a fiber; a
   scheduler runs ready fibers; `join()`/`sleep()` and `Object.wait/notify`
   become real blocking that parks/wakes fibers. Single carrier: millions of
   fibers, no safepoints needed (one mutator at a time).
4. **M:N carriers over CPUs** — a pool of OS carrier threads (`std.Thread`),
   per-core run-queues + work-stealing, safepoint polling at yield points,
   per-carrier TLABs for lock-free allocation, concurrent-safe (stop-the-world)
   GC. This is where true parallelism + the shared-heap safepoint cost lands.

Stage 1 already shipped as a strict subset: iteration 42's single-threaded model
(`Thread.start()` runs `run()` synchronously). See memory
`jebena-threading-decision`.

Invariant every stage: `zig build test` + differential.sh + jbase-smoke.sh +
output-smoke.sh + classpath-smoke.sh + portability-check.sh + `zig fmt --check`
all green before commit. Never commit red.

## Stage 4 design: M:N carriers over CPUs (in progress)

Stages 1–3 are done: one carrier, cooperative, deterministic. Stage 4 adds real
multicore parallelism — a pool of OS **carrier** threads (one per CPU) each
running a scheduler loop over green fibers. This is the only stage with genuine
concurrent mutators, so it is built slowly with green intermediates + stress
tests, and it must never regress the single-carrier gate.

### Shared mutable state audit (what Stage 4 must protect)

- **Heap** (`objects`, `marked`, `old`, `remembered`, `free_list`, `interned`,
  allocation counters): the big one. Object *allocation* mutates `objects`;
  *GC* rewrites everything and (major) moves objects. Strategy: per-carrier
  **nurseries/TLABs** for lock-free fast-path allocation; the shared old gen +
  the whole GC run only at a **safepoint** (all carriers parked), so GC needs no
  fine-grained locking. `interned` is written on string interning — do behind a
  short mutex or at a safepoint.
- **Loader** (`classes`, `statics`, `initialized`, `mirrors`): class loading and
  `<clinit>` mutate these. Class loading is rare; guard with a mutex (or perform
  at a safepoint). `statics` array *contents* are ordinary shared fields (Java
  static fields) — races there are the program's concern (Java memory model), not
  ours, except the backing arrays must not be reallocated concurrently (they are
  allocated once at register time, so reads are safe; a class first-loaded
  concurrently must be serialized).
- **Scheduler** (`ready`, `all`, `current`, `wait_sets`/`waiting_on`): becomes
  per-carrier `ready` queues + work-stealing; `all` (the GC root registry) and
  fiber wake/notify need synchronization (mutex or lock-free deque).
- **Budget.reductions / current fiber**: must move to **per-carrier** state (a
  `Carrier` struct), not shared.

### The safepoint is the key simplification

Because we control the interpreter loop, "stop the world" = each carrier polls a
global `safepoint_requested` flag at its **yield boundary** (the reduction check
in `step()`), and parks when set. GC (and anything needing exclusive heap access)
requests a safepoint, waits for all carriers to park, runs stop-the-world, then
releases. So only **allocation** needs to be concurrent (via per-carrier TLABs);
**everything else that touches the shared heap/loader runs at a safepoint or
under a coarse lock**, which keeps the moving GC exactly as simple as it is today.

### Sub-steps (each gated green; single-carrier stays behaviorally identical)

- **4a** (this): the audit above + a `Carrier` decomposition plan.
- **4b**: safepoint scaffolding — a global flag + `pollSafepoint()` at the yield
  boundary; no-op fast path with one carrier.
- **4c**: per-carrier nurseries/TLABs over the shared old gen (existing write
  barrier + remembered set); single-carrier correct.
- **4d**: real `std.Thread` carriers (one per CPU) + per-carrier ready-queues +
  work-stealing + safepoint-coordinated GC; heavy multi-fiber stress tests.

Correctness over speed at every step. If in doubt, serialize behind the
safepoint or a mutex first, optimize later.

### Toolchain note for 4d (Zig 0.16 concurrency)

This Zig build has NO `std.Thread.Mutex` — sync primitives live under `std.Io`
(the Io-based concurrency model; `std.Io.Mutex` etc.) and `std.atomic`.
`std.Thread.spawn` exists (already used for the big-stack run thread). So 4d
locking = `std.atomic` (spinlocks / `std.atomic.Value`) or `std.Io.Mutex` via the
loader's `io` handle. `Scheduler.safepoint_requested` is a `std.atomic.Value(bool)`.

## Policy (2026-08-21, from the user): thread-safety is now non-negotiable

Every feature added from here on MUST:
1. be **thread-safe** (correct under the M:N carrier model — guard shared state
   or keep it per-fiber/per-carrier; no going back to thread-unsafe shortcuts),
2. keep the **full gate green** (never red), and
3. ship with a **threaded test** exercising it under concurrency (extend
   scripts/thread-stress.sh or add a threaded case; run at carriers 1 & 4).
