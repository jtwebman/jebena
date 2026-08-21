# Research

Method: for each subsystem, survey recent PL/compiler research before choosing an
approach. Prefer modern techniques over "simple and known" when the modern one is
better *or* simpler (often it is both). Each subsystem gets a deep-dive doc that
records the options, the papers, the decision, and why.

Target toolchain: **Zig 0.16.0** (released 2026-04-14). Pin it; 0.17 is in dev.

## Zig 0.16 facts that shape the design

- `std.Io`: colorless async I/O passed as an explicit parameter, like allocators.
  Adopt the same discipline everywhere — pass allocators and I/O explicitly. Maps
  cleanly onto a JVM where GC context / allocation must be explicit anyway.
- Incremental compilation (`-fincremental`): millisecond rebuilds. Use it.
- Guaranteed tail calls via `@call(.always_tail, ...)` — enables a tail-call
  threaded interpreter (see below) without relying on C `musttail`.
- Style: camelCase fns/vars, PascalCase types, UPPER_SNAKE_CASE consts,
  snake_case file-namespaces. Reference large-codebase style: Bun's Zig guide.
  https://ziglang.org/news/0.16.0-released/ | https://github.com/oven-sh/style-guide

## The candidate "modern stack" (to be confirmed per subsystem)

| Subsystem | Old default | Modern candidate | Why |
|---|---|---|---|
| Interpreter | giant switch | **tail-call threaded** (`@call(.always_tail)`) | branch prediction + inline caches; ~10% over computed-goto in CPython 3.14 |
| Baseline JIT | hand-written codegen | **copy-and-patch** (binary stencils) | very fast compile, good code, small; CPython 3.13+/PEP 744 |
| Optimizing JIT | sea-of-nodes (C2) | **e-graph + ISLE rewrite rules** (Cranelift-style) | no phase-ordering problem; declarative rules are individually testable (fits AI-authored + provenance) |
| GC | hand-rolled generational | **Immix heap + LXR** (RC + regions + judicious copying) | LXR: 7.8x throughput, 10x tail latency vs Shenandoah (PLDI'22); productionized 2025 |
| GC framework | monolithic | **MMTk** (pluggable plans over C ABI) or native Immix in Zig | swap collectors without recompiling the VM; ties to the "way different memory" goal |

Note: this is exactly the "way different memory management, still a JVM" thread —
LXR *is* the RC + region hybrid, and MMTk makes the collector pluggable.

## Deep-dive docs (TODO — one per subsystem)

- [x] 01-classfile-verifier  PARSER + mUTF-8 + descriptors DONE, fuzzed. Verifier (Pass 2/3) = next.
- [x] 02-interpreter  DESIGN DONE + labeled-switch interpreter running int/control-flow subset on real bytecode. Next: object model, invokes, more types.
- [x] 03-gc  DONE (see 03-gc.md): spec constraints + LXR target + native-grow plan
- [ ] 04-jit-baseline         (copy-and-patch stencils)
- [ ] 05-jit-optimizing       (e-graph/ISLE vs sea-of-nodes)
- [ ] 06-jmm-concurrency      (memory model, safepoints, biased/lightweight locking)
- [ ] 07-class-libraries      (java.base strategy)

## Key references

- Copy-and-patch: Xu & Kjolstad. https://arxiv.org/pdf/2011.13127 | PEP 744 https://peps.python.org/pep-0744/
- Tail-call interpreter: LWN https://lwn.net/Articles/1010905/ ; Context Threading http://www.cs.toronto.edu/~matz/pubs/demkea_context.pdf
- Cranelift e-graphs/ISLE: https://lwn.net/Articles/965368/ , https://lwn.net/Articles/964735/
- LXR: https://www.steveblackburn.org/pubs/papers/lxr-pldi-2022.pdf ; arXiv https://arxiv.org/abs/2210.17175 ; productionizing (2025) https://www.steveblackburn.org/pubs/papers/prod-oopsla-2025.pdf
- MMTk: https://www.mmtk.io/about ; https://docs.rs/mmtk/latest/mmtk/
- Immix (foundational): mark-region heap; basis for LXR and MMTk's Immix plan.
- Catalpa (low-variance GC, 2025): https://arxiv.org/pdf/2509.13429
