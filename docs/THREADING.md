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

## Stage 4d-4-iii(b2): real std.Thread carriers — LANDED (opt-in), 2026-08-21

Real multicore parallelism now works. `JEBENA_CARRIERS=N` (N>1) spawns N-1
`std.Thread` carriers; carrier 0 runs on the main OS thread. Each carrier owns
its own `Budget` and runs `Scheduler.carrierLoop`: pop a ready fiber, re-point
its frames at the carrier's budget, drive it, requeue on yield / retire on
completion (`outstanding--`). Carriers exit when `outstanding==0` (all fibers
done) or `shutdown` is set; the main thread joins the workers and returns the
main fiber's result. `runMulti` orchestrates spawn+join; `runFiber` takes it only
for the top-level run (`carriers_total==1` guard), so nested native re-entry
stays single-carrier.

**Blocking under multiple carriers is spin-wait, not parking (for now).** A
native (`Thread.join0`) can't suspend the interpreter, so when `carriers_total>1`
the joining carrier busy-waits `while (target.status != .done)`, polling the
safepoint each spin, while the *other* carriers run the joinee. Correct and
simple; real parking (yield the carrier back to `carrierLoop`) is a later
optimization. The single-carrier path still inline-pumps the run-queue.

**Proven:** `scripts/thread-stress.sh` runs 8 fibers × 1000 (AtomicInteger +
AtomicLong) at `JEBENA_CARRIERS=4`, 10 reps, timeout-guarded: total is exactly
24000 every time (no lost updates, no crash, no hang) AND `JEBENA_CARRIER_TRACE`
confirms ≥2 (in practice all 4) distinct carriers ran work — genuine
parallelism, not a serialized fallback. The default (1 carrier) stays
byte-identical and deterministic; parallelism is strictly opt-in.

**Why the stress workload is race-free here:** once running, the fibers touch
only locked atomics (AtomicInteger/Long natives under `atomics_lock`) and the
locked ready-queue; they allocate nothing in the hot loop (so no GC fires) and
load no classes (all linked during single-threaded startup). So the moving GC
and lazy class-loading paths are simply not exercised concurrently.

### Hardening update (2026-08-21): concurrent GC coordination fixed; concurrent ALLOCATION is the remaining blocker

Three real GC bugs found and fixed while building an allocating stress test:
- **Double-request deadlock:** two carriers both hitting `maybeCollect` each called
  `requestSafepoint` and waited for the other to park. Fixed with a `gc_lock`:
  exactly one carrier collects; the rest park via their normal safepoint polls.
- **Nested re-entry fibers weren't GC roots:** lambda bodies / native re-entry run
  on a local `exec` fiber not in `scheduler.all`. At one carrier the collector's
  parent-chain walk covered it; across carriers, *other* carriers' exec fibers were
  invisible → their live objects were freed. Fixed by registering every `exec`
  fiber in `scheduler.nested` and rooting `all + nested`.
- **Mark/remap asymmetry:** marking walked the parent chain *and* the fiber lists
  while remap used only the lists, so a frame covered one way but not the other left
  stale ids. Fixed by making both iterate exactly `all + nested` (every live frame
  belongs to exactly one fiber's call_stack — a scheduled fiber or a nested exec).

With these, **single-carrier moving GC under heavy allocation is solid**
(`scripts/alloc-gc-stress.sh`: 8 fibers × 2000 allocs, GC forced every 200–2000
allocs, exact match to real java, in the gate).

### SOLVED (2026-08-21, iter 70): concurrent allocation + concurrent moving GC across real carriers

The object table was migrated from `std.ArrayList` to a **paged table** (`Pages(T)`):
a fixed-capacity directory of fixed-size (4096-slot) pages. Page slices never move
once allocated, so `get(id)` for any committed id is a **lockless read with no
torn-realloc** -- `put()` appends under `alloc_lock` and allocates a new page only
when crossing a page boundary; the directory and existing pages are never
reallocated. `objects`, `marked`, `old`, and `remembered` all use it; GC (at a
safepoint, exclusive) iterates/compacts/shrinks by index.

With the paged table plus the iter-69 GC coordination fixes, **concurrent
allocation AND concurrent moving GC now work across real carriers.**
`scripts/alloc-gc-stress.sh` runs AllocStress (8 fibers x 2000 array allocations)
at `JEBENA_CARRIERS=4` with no forced GC, with GC every 1000 allocs, and with
aggressive GC every 300 allocs -- exact `257792000` every time, all 4 carriers
ran, no crash/hang, 30+ reps, timeout-guarded, zero new leaks. Opt-in parallelism
is now safe for **allocating** workloads too, not just atomics.

Remaining N>1 gaps (smaller): per-carrier `budget.pending` isn't rooted/remapped
across carriers (only the collector's) -- harmless today (stress paths throw no
exceptions); plus the items in the list below.

**Known limitations (opt-in path only; default single carrier unaffected) —
harden next:**
- `Object.wait/notify` under `carriers_total>1` is NOT yet correct: the native
  finds the calling fiber via `self.carrier.current`, which is null when workers
  run on their own carrier structs. Not exercised by the gate at N>1 (the
  wait/notify smoke runs at the default 1 carrier). Needs a frame→fiber lookup +
  a spin-on-notified path mirroring `join0`.
- Concurrent **lazy class loading / `getMirror` / string interning**: FIXED
  (iter 71). A reentrant, safepoint-polling `Loader.load_lock` serializes
  `resolveClass` (find-or-load-and-register), `getMirror`, `internLiteral`, and
  `ensureInit`/`<clinit>` across carriers. Reentrant because loading recurses on
  the same carrier (superclass, `<clinit>`-triggered loads); the spin polls the
  safepoint because the lock is held across allocation/`<clinit>` that can trigger
  GC (a non-polling waiter would deadlock the collector). Proven by
  `scripts/load-stress.sh`: 8 fibers concurrently first-load 12 classpath-dir
  classes + intern distinct literals at carriers 1 & 4 (+ forced GC), exact vs
  real java.
- **Concurrent GC**: FIXED (iter 70, paged table) -- see "SOLVED" above.
- Pre-existing: ~18 `DebugAllocator` leaks on the StressMain workload (in
  `primitiveClass`'s `gpa.dupe` and friends) — present at HEAD before this change,
  identical count at 1 and 4 carriers, so not a parallelism regression. Fix
  separately.

## Policy (2026-08-21, from the user): thread-safety is now non-negotiable

Every feature added from here on MUST:
1. be **thread-safe** (correct under the M:N carrier model — guard shared state
   or keep it per-fiber/per-carrier; no going back to thread-unsafe shortcuts),
2. keep the **full gate green** (never red), and
3. ship with a **threaded test** exercising it under concurrency (extend
   scripts/thread-stress.sh or add a threaded case; run at carriers 1 & 4).
