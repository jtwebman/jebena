# Backlog — Jebena

Prioritized backlog for the clean-room Java runtime. IDs are stable (JB-NNN).
Priority: P0 = active lane, P1 = next, P2 = later, P3 = long horizon.

**Honest scope:** this is a from-scratch JVM *and* a from-scratch clean-room
java.base, moving fast under a tight differential-testing loop. Realistic horizon:
**months to a genuinely "working" runtime, and a few more months to something you
could actually use** for real applications. What exists today is real and tested —
an early but substantial slice, not a toy. See `docs/ROADMAP.md` for the phase plan
and `docs/LOOP-LOG.md` for the running history.

**Why it's worth building** (in 2026, from scratch): it's a proof that AI-written,
human-reviewed code holds up on a hard systems project; green threads (M:N scheduler)
and a moving GC are in the core from day one rather than retrofitted; and it's free
to make modern, clean-slate design choices with no 30 years of legacy. Every change
is checked byte-for-byte against real OpenJDK and never lands red.

If you would genuinely want to see this finished (or want to help/fund/use it),
email **jtwebman@gmail.com** — interest is the main thing that would justify
pouring more time into it.

## Done so far (high level)

- [x] JB-001 Class-file parser + verifier + bytecode interpreter (real bytecode runs).
- [x] JB-002 Clean-room `java.base` as compiled Java (`jbase/`), bytecode-primary
      with a Zig native-method registry + optional intrinsics.
- [x] JB-003 Core `java.lang`: Object, String (char[]-backed, interned), boxing
      (Integer/Long/Double/…), Math, full exception hierarchy, StringBuilder/Buffer.
- [x] JB-004 Collections + utils: ArrayList, LinkedList, ArrayDeque, HashMap/Set,
      TreeMap/Set, LinkedHashMap (+access-order LRU), PriorityQueue, Collections,
      Arrays, Comparator, Optional, Objects, BitSet, Base64, regex, Formatter.
- [x] JB-005 Streams + functional: Stream/IntStream/LongStream/DoubleStream,
      Collectors (groupingBy/partitioningBy/teeing/…), java.util.function SAMs.
- [x] JB-006 java.time: LocalDate/Time/DateTime, Instant, Duration/Period, ZoneOffset,
      Year/YearMonth/MonthDay, DateTimeFormatter, Clock.
- [x] JB-007 java.math: BigInteger, BigDecimal, MathContext, RoundingMode.
- [x] JB-008 Reflection: Method.invoke, Field get/set, Constructor.newInstance,
      getDeclared*; annotations (RuntimeVisibleAnnotations) + Proxy.
- [x] JB-009 Threads: java.lang.Thread, monitors/synchronized, wait/notify, an M:N
      green-thread scheduler over carriers, moving mark-compact GC.
- [x] JB-010 java.util.concurrent slice: atomics + atomic arrays, locks/Condition,
      CountDownLatch, CyclicBarrier, Semaphore, ConcurrentHashMap, ConcurrentLinkedQueue,
      ConcurrentLinkedDeque, CopyOnWriteArrayList, LinkedBlockingQueue, Executors/ThreadPoolExecutor.
- [x] JB-011 I/O + net slice: InputStream/OutputStream/Reader/Writer, PrintStream,
      Socket/ServerSocket; a small JDBC + Postgres path exercised by stress scripts.
- [x] JB-012 Differential-testing harness: 1200+ byte-for-byte cases vs OpenJDK 21,
      real-program smokes, and threaded stresses at carriers 1 & 2 (+GC). A ~60-script
      gate must be fully green before every commit.

## Active lane (P0)

- [ ] JB-020 Keep growing the differential surface (it finds real bugs every batch).
- [ ] JB-021 VM depth fixes from `memory jebena-open-vm-findings`:
      constructor method refs (`ArrayList::new`, REF_newInvokeSpecial/impl_kind 8);
      `ConcurrentHashMap.keySet().iterator()`; `Arrays.deep*` on nested arrays.
      (Transitive super-interface instanceof/checkcast — FIXED.)
- [ ] JB-022 Toolchain: install a JDK ≥ 18 `javac` so SE18+ APIs (Math.ceilDiv/clamp)
      can be differentially oracle-tested; currently only JDK 17 javac is present.

## Next (P1)

- [ ] JB-030 Broaden java.util / java.util.concurrent coverage and edge cases.
- [ ] JB-031 Class init ordering / `<clinit>` dependency graph hardening.
- [ ] JB-032 ClassLoader + JAR loading; classpath completeness.
- [ ] JB-033 MethodHandles / broader invokedynamic; dynamic Proxy completeness.
- [ ] JB-034 getMethod-by-signature, fuller virtual dispatch, annotation defaults.

## Later (P2)

- [ ] JB-040 Performance: inline caches + bytecode quickening.
- [ ] JB-041 Copy-and-patch baseline JIT (own build-time stencils; clean-room).
      See `memory jebena-jit-plan`.
- [ ] JB-042 Production GC (Immix byte-heap → LXR) replacing the mark-compact collector.
- [ ] JB-043 java.util.concurrent performance + fuller memory-model conformance.

## Long horizon (P3)

- [ ] JB-050 JDBC + embedded DB (H2 in-process) before any networked DB.
- [ ] JB-051 Enough reflection/proxies/annotations for a DI container.
- [ ] JB-052 Spring is the north star — the honest end goal, and honestly far away.

## Guardrails (things we deliberately do NOT do)

- Do NOT copy, translate, or refactor OpenJDK source. Implement from the JLS/JVMS.
  Behavior may be observed as a test oracle; expression must be independent.
- Do NOT "edit copied code to look different" — that manufactures a derivative work.
  The rule is flag → discard → re-derive from the spec in a fresh context.
- Do NOT commit red. Every change keeps `zig build test` + the full script gate green.
- Do NOT ship anything as production-ready. A real IP lawyer must sign off on the
  provenance wall before anything ships. This is not legal advice.
