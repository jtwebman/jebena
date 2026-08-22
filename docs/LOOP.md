# Loop Charter — how the autonomous loop runs

Each iteration is one small, verified, committed step. Never leave the tree red.

## Per-iteration procedure
1. Read docs/ROADMAP.md, the MEMORY, and docs/research/TODO.md.
2. Pick the single highest-priority item that is currently tractable.
3. Implement it: production code + unit tests; add differential cases in
   test/diff/*.java + scripts/differential.sh whenever the feature is observable
   from a compiled Java program.
4. VALIDATION GATE (all must pass before committing):
   - `zig build test` (via ~/.local/zig-x86_64-linux-0.16.0/zig) — all unit tests green
   - `bash scripts/differential.sh` — byte-identical to the JDK21 oracle (SE-conformant)
   - `bash scripts/jbase-smoke.sh` + `bash scripts/output-smoke.sh` — our own clean-room java.base bytecode runs correctly
   - `bash scripts/classpath-smoke.sh` — lazy directory classpath loading matches real java
   - `bash scripts/thread-stress.sh` — green-thread scheduler + AtomicInteger under many fibers (carriers 1 & 4)
   - `bash scripts/portability-check.sh` — VM cross-compiles for macOS/Linux (aarch64/x86_64)
   - `zig fmt --check src/ build.zig` — formatted
   If anything fails: fix it, or revert the change. Do not commit red.
5. Commit with a clear message (end with the Claude-Session line). Push if a
   remote is configured (`git push`), else note it's local-only.
6. Update docs/ROADMAP.md / TODO.md: tick done, add any discovered sub-tasks.
7. Append one line to docs/LOOP-LOG.md: `YYYY-MM-DD HH:MM  <what changed>  (tests N, diff M)`.
8. Refresh the memory progress note if a milestone was reached.

## Rules
- Correctness first: the differential suite vs real `java` is the source of truth.
- Small commits: one focused change each, always green.
- When a task is large, use a workflow to fan out (implement many natives at once,
  broaden differential coverage, adversarial review) — allowed.
- If blocked/stuck on an item, log it, skip to the next tractable item; don't stall.
- Keep the differential suite growing — it is the bug-finder.
- Toolchain: Zig 0.16.0 at ~/.local/zig-x86_64-linux-0.16.0/zig. javac 17 for fixtures.
