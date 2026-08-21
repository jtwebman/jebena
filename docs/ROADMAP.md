# Jebena Roadmap — toward a full Java JVM

Priority order for the autonomous loop. Work top-down; within a phase, take the
highest-value item that is currently tractable. Every change must keep
`zig build test` AND `scripts/differential.sh` green before commit (see LOOP.md).

## Phase 0 — language & intrinsic completeness (tractable, keep momentum)
- [ ] StringBuilder / StringBuffer (append/toString/insert/length/charAt/reverse)
- [x] this-capturing lambdas + instance-method references (obj::method, bound refs)
- [ ] float/double -> String (Java shortest-round-trip; match Double.toString)
- [x] boxing: Integer/Long/Double/Float/Boolean/Character/Short/Byte (valueOf/xxxValue/equals/hashCode/parse/toString); [ ] generic functional interfaces w/ box-unbox adaptation
      functional interfaces + autoboxing work
- [ ] remaining opcodes / edge cases surfaced by differential fuzzing
- [ ] more intrinsics: Character, more Math/Integer/Long/String, Objects, Arrays.*
- [ ] Comparator + Arrays.sort(T[], Comparator) / Collections.sort (needs lambdas+boxing)
- [ ] keep GROWING scripts/differential.sh every iteration (it finds real bugs)

## Phase 1 — the real java.base (the "hard 20%", most of a real JVM)
- [ ] extract java.base .class files from the installed JDK (jmods/modules)
- [ ] load real java/lang/Object (identity hashCode, getClass, equals/hashCode)
- [ ] a native-method registry: methods marked ACC_NATIVE dispatch to Zig intrinsics
- [ ] real java/lang/String (byte[] value + coder; StringLatin1/UTF16 intrinsics)
- [ ] real java/lang/Class + Object.getClass (minimal)
- [ ] real java.util: Objects, AbstractCollection/List, ArrayList, HashMap, HashSet,
      LinkedList, Arrays.asList, Optional (mostly pure Java once Object/arrays work)
- [ ] real Number/Integer/Long/Double boxing classes
- [ ] real java.lang.System (out as a working PrintStream to stderr), arraycopy (done)
- [ ] real exceptions with messages + getMessage + stack traces
- [ ] class init ordering, <clinit> dependency graph, real static init

## Phase 2 — reflection & dynamic
- [ ] Class.forName, getName, getDeclaredMethods/Fields/Constructors
- [ ] Method.invoke, Field get/set, Constructor.newInstance (reflective call into interp)
- [ ] annotations: RuntimeVisibleAnnotations decode + getAnnotation
- [ ] MethodHandles / dynamic proxies (Proxy.newProxyInstance)

## Phase 3 — runtime services
- [ ] threads: java.lang.Thread, start/join, real monitors (single or multi), volatile
- [ ] I/O: files, InputStream/OutputStream/Reader/Writer via std.Io
- [ ] networking: Socket/ServerSocket (via std.Io/posix)
- [ ] collections perf, java.util.concurrent basics
- [ ] classpath: JAR loading, the module system

## Phase 4 — performance
- [ ] method inline caches + quickening (02 design)
- [ ] copy-and-patch baseline JIT (04 design)
- [ ] the real subsystem-03 GC: Immix byte-heap -> LXR

## Phase 5 — application stack (long horizon)
- [ ] JDBC + embedded DB (H2 in-process before any network DB)
- [ ] enough reflection/proxies/annotations for a DI container
- [ ] Spring is the north star; not near-term
