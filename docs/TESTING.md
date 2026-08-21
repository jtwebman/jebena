# How Jebena Is Tested

Modeled on SQLite's testing discipline. The point is not the count of tests. It
is the variety of independent attack, and the principle that trust comes from
relentless verification rather than from who — or what — wrote the code.

## Principles

1. Independent, redundant oracles. The biggest risk of AI-written code and
   AI-written tests is a shared blind spot: author and examiner make the same
   mistake and the test confirms the bug. Defend against it structurally.
   - Reference JVMs are a guaranteed-independent oracle. They never read our code.
   - Use different agents/strategies for implementation and for test generation.
   - Fuzzing and property-based generation produce inputs no author imagined.

2. Coverage is a measured gate, not an aspiration. Target 100% MC/DC on the
   as-deployed build. If a branch cannot be reached by a test, question why the
   branch exists.

3. Test the as-deployed configuration. Exercise the real tiered setup — the
   optimizer, not just the interpreter. No gap between the thing tested and the
   thing shipped.

4. Anomaly paths are first-class. Inject OutOfMemoryError at every allocation
   site. Stress safepoints, deopt, class-loading failure, GC under starvation.
   The unhappy paths are where real runtimes die.

5. The suite is a ratchet. Every bug ever found becomes a permanent regression
   test. Monotonic, never deleted.

6. The code verifies itself. Dense assertions and invariant checks in the GC/JIT
   arena, run continuously under sanitizers — the safety we gave up by not using
   Rust, bought back as executable invariants.

7. Discipline is the moat. This is built to last. The severity of the testing is
   the reason to trust it, and the answer to "an AI wrote it."

## The arsenal

- Differential / oracle testing. Run the same program on Jebena and on reference
  JVMs; divergence is a bug or a spec ambiguity. The workhorse. Provenance-clean.
- Metamorphic testing for the JIT. Interpreter vs C1 vs C2 vs -Xcomp must produce
  identical observable results. The program is its own oracle.
- Bytecode fuzzing. Malformed .class files against the verifier and interpreter.
  The verifier must reject bad input safely — treat it like a network parser.
- Source fuzzing. Generated Java programs against the compiler.
- Concurrency / JMM stress. Litmus tests exploring weak-memory interleavings.
  Needs specialized tooling, not just volume.
- Property-based testing for the libraries. Invariants, thousands of cases each.
- Sanitizers (ASan/UBSan/TSan) plus Zig safety builds over the native core.

## Conformance scoreboard

Publish a spec-conformance scoreboard across Jebena and the reference JVMs, tests
derived from the JLS/JVMS independently of Jebena's implementation. Categorize
every divergence honestly:

- A. OpenJDK deviates from a clear spec  -> a real finding, file it upstream.
- B. Jebena deviates from a clear spec   -> our bug, report it anyway.
- C. Spec is ambiguous                   -> flag it for the spec.
- D. OpenJDK behavior is the de-facto standard -> matching it is correct; being
     stricter than intent is our bug, not a win.

Only A is a win. Publishing B, C, and D is what makes A believable. The strongest
result is not "we pass more tests than OpenJDK" — it is "we found genuine bugs in
OpenJDK, upstreamed and accepted, and reported our own failures in the open."

Note: the official TCK/JCK is license-gated by Oracle and cannot be used freely.
Differential testing against the reference implementations is the substitute.
