# 02 — Interpreter

Decision doc for Jebena's bytecode interpreter. Target: Java SE 25, Zig 0.16.
The interpreter is Tier 0: correct, simple, and the profiling source that feeds
the JIT tiers later.

## 1. What the spec fixes

JVMS fully specifies *execution semantics* (unlike GC): 202 opcodes, a stack-based
model with a per-frame operand stack + local variable array, exact type rules,
exception behavior, and linking/resolution. The interpreter has no design freedom
in *what* a bytecode does — only in *how* it dispatches and how fast it runs.

Relevant fixed points:
- Per-frame operand stack + locals (JVMS 2.6). long/double take two slots.
- Lazy resolution of constant pool references (JVMS 5.4) — resolve on first use,
  with the specified errors. This is the hook for quickening (below).
- Exceptions via per-method exception tables; specified stack unwinding (JVMS 2.10).
- Bytecode verification (stackmap frames, JVMS 4.10) runs BEFORE execution. After
  verification the interpreter may trust types — no per-op type checks needed.
- invokedynamic + bootstrap methods + method handles (JVMS 5.4.3.6) — the hard part.
- Safepoints: not a spec concept, but the interpreter loop must poll for GC/thread
  ops (ties to 03-gc barriers and root scanning).

## 2. Dispatch — decision: labeled switch/continue

Zig 0.14+ has labeled switch/continue (`continue :sw next`), added specifically for
interpreter dispatch. It is a computed-goto in SAFE Zig: the compiler emits a
separate indirect jump per opcode arm, spreading the dispatch so the branch
predictor learns opcode-pair sequences. This is both the modern choice and the
simple one — no unsafe, no reliance on C musttail.

Options considered:
- plain `switch` in a loop: one shared indirect jump; poor prediction. Rejected.
- labeled switch/continue: computed-goto equivalent, safe Zig. CHOSEN.
- tail-call threading (`@call(.always_tail)`, one fn per opcode): equivalent perf,
  matches CPython 3.14's ~10% win. Keep as the structure IF/when we move opcode
  bodies into per-op functions for copy-and-patch stencils (see 04-jit-baseline).
- template interpreter (HotSpot-style per-op machine-code stubs): this is codegen.
  SKIP IT — the copy-and-patch baseline JIT (04) subsumes its purpose. HotSpot has
  a template interpreter because it predates cheap baseline JITs; we don't need one.

## 3. Optimization ladder (proven, high ROI, in order)

1. Quickening (JVMS-compatible): after first execution resolves a constant-pool
   entry, rewrite the bytecode in a private copy to a `_fast_` internal variant
   that skips re-resolution (e.g. getfield -> getfield_quick with a resolved
   offset). Classic JVM + CPython technique. Individually testable.
2. Inline caches at invokevirtual/invokeinterface: cache resolved class->method at
   the call site (monomorphic, then polymorphic). Pairs with quickening
   (Brunthaler, "Inline Caching Meets Quickening", ECOOP'10). Also the profiling
   substrate the JIT reads.
3. Top-of-stack caching: keep the top operand-stack slot(s) in a register/local
   instead of memory (Ertl & Gregg, PLDI'95). Optional; measure first.
4. Superinstructions: fuse common opcode sequences to cut dispatch count (Ertl,
   Cacao). Later optimization; measure first.

Do 1 and 2 early (they also define the JIT handoff). 3 and 4 are measure-driven.

## 4. JVM-specific concerns

- Constant pool + lazy linking: resolution state machine per entry; quickening is
  the caching layer over it.
- invokedynamic / method handles: bootstrap-method call sites, CallSite objects,
  MethodHandle invocation. Genuinely hard; needed for lambdas, string concat,
  records, pattern matching. Stage it after basic invokes work.
- Exceptions: exception-table lookup + unwind; interpreter must run finally/synchronized
  exits correctly.
- Frames: operand stack + locals + pc; decide frame layout early — it is the GC
  root source.
- GC roots (ties to 03-gc): the collector needs to know which operand-stack/local
  slots hold object references at each safepoint (oop maps). Options:
  a) precise maps derived from the verifier's stackmap frames (HotSpot approach),
  b) a tagged stack (one tag bit/slot; simple, small overhead),
  c) conservative stack scanning (simplest, but pins objects; bad for moving GC).
  Decision: start with (b) tagged stack for a moving-GC-friendly, simple Phase-0;
  move to (a) precise maps for performance once Immix/LXR lands.
- Safepoint polling: check a per-thread flag at backward branches + method entry
  (and invokes). Cooperates with 03-gc STW pauses.

## 5. Interfaces

- To GC (03): frame layout + oop maps (roots), write-barrier calls on putfield/
  aastore, allocation on new/newarray, safepoint polls.
- To JIT (04/05): quickened + inline-cache-profiled bytecode, plus branch/type
  counters, are the input to the copy-and-patch baseline and the optimizing tier.
  Design the profiling counters into the interpreter from the start.

## 6. Phased plan

- Phase 0 — Decode + labeled-switch dispatch over all 202 opcodes on VERIFIED
  bytecode; tagged operand stack; basic invokes; exceptions; safepoint poll.
  Goal: run real methods. This is the mutator that exercises Phase-0 GC.
- Phase 1 — Quickening + inline caches; constant-pool resolution state machine.
- Phase 2 — invokedynamic / method handles (lambdas, string concat, records).
- Phase 3 — Precise oop maps from stackmaps; TOS caching / superinstructions if
  measurement justifies. Wire profiling counters to the JIT.

## 7. Test hooks (see ../TESTING.md)

- Differential vs reference JVMs on generated bytecode; metamorphic: interpreter
  result must equal JIT result (-Xint vs compiled).
- Bytecode fuzzing hits the verifier first; interpreter trusts verified input, so
  test the verifier/interpreter boundary hard.
- Exception-path and finally/synchronized-exit coverage (MC/DC).
- Safepoint/GC-root correctness under forced GC at every safepoint.

## References
- Zig labeled switch/continue: https://github.com/ziglang/zig/pull/21257 ; https://simonklee.dk/labeled-switch
- Tail-call interpreter (CPython 3.14): https://lwn.net/Articles/1010905/
- Inline Caching Meets Quickening (Brunthaler, ECOOP'10): https://publications.sba-research.org/publications/ecoop10.pdf
- Stack Caching for Interpreters (Ertl & Gregg, PLDI'95): https://dl.acm.org/doi/10.1145/207110.207165
- Superinstructions / Cacao (Ertl): https://www.complang.tuwien.ac.at/andi/papers/dotnet_06.pdf
- Inline-threaded Java w/ preparation sequences (Gagnon & Hendren)
- JVMS 25 ch. 2, 4.10, 5.4, 6 — see ../specs
