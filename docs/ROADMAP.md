# Jebena Roadmap — toward a full Java JVM

Priority order for the autonomous loop. Work top-down; within a phase, take the
highest-value item that is currently tractable. Every change must keep
`zig build test` AND `scripts/differential.sh` green before commit (see LOOP.md).

## Phase 0 — language & intrinsic completeness (tractable, keep momentum)
- [ ] StringBuilder / StringBuffer (append/toString/insert/length/charAt/reverse)
- [x] this-capturing lambdas + instance-method references (obj::method, bound refs)
- [x] float/double -> String (IEEE shortest round-trip, matches SE19+/JDK21 Double.toString; oracle pinned to JDK21 since JDK17 FloatingDecimal is non-shortest)
- [x] boxing MIGRATION complete: clean-room Number + Integer/Long/Double/Float/Boolean/Character/Short/Byte as real classes (value fields, valueOf caches for == identity, xxxValue/equals/hashCode/compareTo/toString/parse, Double/Float bit natives). Zig BoxedObj remains the stub path. FIXED long/double instance fields (2-slot pop/push)
      functional interfaces + autoboxing work
- [ ] remaining opcodes / edge cases surfaced by differential fuzzing
- [x] Character intrinsics (isDigit/isLetter/isWhitespace/case/digit/compare/getNumericValue) + String toUpperCase/toLowerCase/trim/strip/equalsIgnoreCase/indexOf(II)/lastIndexOf/repeat/toCharArray; [ ] more Math/Objects/Arrays
- [x] Comparator + Arrays.sort(T[], Comparator): lambda/method-ref comparators, stable, re-enters interpreter per compare; [ ] Collections.sort (needs List)
- [ ] keep GROWING scripts/differential.sh every iteration (it finds real bugs)


## Phase 1 — our OWN clean-room java.base (COMMITTED, 2026-08-21)

Write java.base ourselves as clean-room Java SOURCE (from the spec), compile to
bytecode, and run it on our VM. NOT OpenJDK's classes (GPL + not clean-room); NOT a
pure-Zig library (breaks reflection/subclassing). Bytecode-primary; Zig provides the
native-method registry and optional hot-path intrinsics. This is the 100%-from-scratch
stack that honors the project thesis.

Steps (each a loop iteration or few):
- [x] jbase/ source tree + scripts/build-jbase.sh (javac --patch-module java.base -> jbase/out)
      compilation of our own java/lang (may need --patch-module / -Xbootclasspath).
- [x] cmdRun: a provided real .class (e.g. jbase Object) overrides the Zig stub of the same name
- [x] native-method registry: ACC_NATIVE methods resolve then dispatch to Zig by (owner,name,desc); seeded with Object.identityHashCode
      native impls (Object identity-hashCode/getClass, System.arraycopy, float<->bits).
- [x] clean-room java/lang/Object (equals/hashCode/native identityHashCode) loaded + executed as real bytecode (scripts/jbase-smoke.sh: 111)
      execute a method from OUR compiled bytecode end-to-end (prove the pipeline).
- [x] migration switch: Class.is_stub; intrinsics defer to a loaded real class (invokeStatic/invokeInstance). [x] clean-room java/lang/Math (abs/max/min/floorDiv/floorMod) runs as real bytecode. [ ] clean-room String/boxing next
      Double boxing, Math, Number — each replacing a Zig stub, differential-verified.
- [ ] clean-room java.util core: Objects, AbstractCollection/List, ArrayList, HashMap,
      HashSet, Arrays, Comparator, Collections.
- [ ] <clinit> dependency ordering for the core set.
- [ ] keep ALL prior differential tests green throughout (regression guard); migrate
      stubs -> real classes one at a time.

## Phase 1 — the real java.base (the "hard 20%", most of a real JVM)
- [ ] extract java.base .class files from the installed JDK (jmods/modules)
- [ ] load real java/lang/Object (identity hashCode, getClass, equals/hashCode)
- [ ] a native-method registry: methods marked ACC_NATIVE dispatch to Zig intrinsics
- [x] clean-room java.lang.String (char[]): full method set as real bytecode (concat/substring/indexOf(int,String)/lastIndexOf/startsWith/endsWith/compareTo/equalsIgnoreCase/trim/toUpperCase/toLowerCase/replace/toCharArray/valueOf) + invokedynamic concat + GC-rooted interning; all string producers representation-aware (newString->makeString). jbase-smoke matches OpenJDK
- [ ] real java/lang/Class + Object.getClass (minimal)
- [~] java.util: Objects, ArrayList(List), HashMap(Map), HashSet(Set) with Collection/List/Set/Map/Map.Entry interfaces (polymorphic declared types + invokeinterface work), keySet/values/entrySet views. Remaining: StringBuilder real, Collections/Comparable, LinkedList/TreeMap
      LinkedList, Arrays.asList, Optional (mostly pure Java once Object/arrays work)
- [ ] real Number/Integer/Long/Double boxing classes
- [ ] real java.lang.System (out as a working PrintStream to stderr), arraycopy (done)
- [x] clean-room Throwable/Exception/RuntimeException/IllegalArgumentException: real detailMessage field, super(message) constructor chain, getMessage/getLocalizedMessage, throw/catch subtype matching (jbase-smoke 209)
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
