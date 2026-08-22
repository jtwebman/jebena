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
| Per-process heaps (share-nothing) → independent per-process GC, no global stop-the-world | **Cannot fully adopt.** Java threads *share* the heap. So we keep a shared heap and pay with safepoints (only at yield points) + per-carrier TLABs. |

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
