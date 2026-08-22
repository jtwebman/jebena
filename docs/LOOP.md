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
   - `bash scripts/alloc-gc-stress.sh` — moving mark-compact GC under heavy allocation (carriers 1 & 4, concurrent GC)
   - `bash scripts/load-stress.sh` — concurrent lazy class loading + interning across carriers (1 & 4, +GC)
   - `bash scripts/throw-stress.sh` — exception throw/catch + allocation under concurrent GC (carriers 1 & 4)
   - `bash scripts/sync-stress.sh` — synchronized (reentrant monitors) mutual exclusion (carriers 1 & 4, +GC)
   - `bash scripts/waitnotify-stress.sh` — wait/notify + monitor release/reacquire (carriers 1 & 4, +GC)
   - `bash scripts/juc-stress.sh` — java.util.concurrent CountDownLatch + Semaphore (carriers 1 & 4, +GC)
   - `bash scripts/join-stress.sh` — fiber parking: 16 joiners on 1 worker, 16 > carriers (1 & 4, +GC)
   - `bash scripts/queue-stress.sh` — ArrayDeque growth + LinkedBlockingQueue producer/consumer (carriers 1 & 4, +GC)
   - `bash scripts/coll-stress.sh` — LinkedList + ArrayDeque + nested-class instantiation (LinkedList$Node/ArrayDeque$Itr) via directory classpath (carriers 1 & 4, +GC)
   - `bash scripts/net-stress.sh` — java.net loopback echo (Socket/ServerSocket over std.Io TCP), carriers 2 & 4 +GC, skips cleanly if sockets unavailable
   - `bash scripts/http-stress.sh` — hand-written HTTP/1.1 request-parse + 200 response over java.net, carriers 2 & 4 +GC, skips cleanly if sockets unavailable
   - `bash scripts/pg-stress.sh` — Postgres v3 wire client (SELECT 1) vs a spec-faithful mock backend, carriers 2 & 4 +GC; also hits a live Postgres when JEBENA_PGTEST is set + reachable
   - `bash scripts/dbapi-stress.sh` — Postgres-backed HTTP API (GET -> PG SELECT 1 -> HTTP body), carriers 3 & 4 +GC; also end-to-end vs live Postgres when JEBENA_PGTEST is set
   - `bash scripts/rich-sql-stress.sh` — multi-row/multi-column PG result-set parse, carriers 2 & 4 +GC; also live Postgres generate_series when JEBENA_PGTEST is set
   - `bash scripts/router-stress.sh` — multi-endpoint HTTP service (/ping + PG-backed /db + 404), carriers 1 & 4 +GC; live /db when JEBENA_PGTEST is set
   - `bash scripts/executor-stress.sh` — java.util.concurrent thread pool (Executors/ThreadPoolExecutor/Future), 100 tasks summed via get(), carriers 1 & 4 +GC
   - `bash scripts/inject-err-stress.sh` — fiber-error isolation: one worker's uncaught error terminates only that fiber (no carrier death / GC deadlock), total=35, carriers 1 & 4 +GC
   - `bash scripts/breadth-diff.sh` — java.* breadth differential (java.time LocalDate/LocalTime/Duration arithmetic, TreeMap/LinkedHashMap ordered iteration, Collections + Arrays helpers, java.util.regex Pattern/Matcher, java.util.stream pipeline + Collectors) checked byte-for-byte vs real java, exercising clean-room jbase bytecode via EAGER classpath
   - `bash scripts/mainrun-smoke.sh` — real-program entry point: `jebena run <Main> main <cp>... -- <args>` invokes public static void main(String[]) with argv marshalled into a real String[]; full stdout byte-identical to real java
   - `bash scripts/exit-smoke.sh` — program termination semantics: clean exit (status 0), System.exit(n) (status n), and an uncaught exception (java-style "Exception in thread main" stderr first line + status 1), all matching real java
   - `bash scripts/jar-smoke.sh` — `jebena -jar <app.jar> <jbase-dir> -- <args>` reads Main-Class from META-INF/MANIFEST.MF and runs a multi-class app whose classes load lazily from the jar; stdout byte-identical to real `java -jar`
   - `bash scripts/trace-smoke.sh` — full uncaught-exception stack trace (`Exception in thread "main" <type>: <msg>` + `\tat Class.method(File:line)` frames across a multi-level call chain), byte-identical to real java; exercises Throwable.fillInStackTrace + StackTraceElement + LineNumberTable/SourceFile
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
