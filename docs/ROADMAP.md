# Jebena Roadmap — toward a full Java JVM

Priority order for the autonomous loop. Work top-down; within a phase, take the
highest-value item that is currently tractable. Every change must keep
`zig build test` AND `scripts/differential.sh` green before commit (see LOOP.md).

## Phase 0 — language & intrinsic completeness (tractable, keep momentum)
- [ ] StringBuilder / StringBuffer (append/toString/insert/length/charAt/reverse)
- [x] this-capturing lambdas + instance-method references (obj::method, bound refs)
- [x] float/double -> String (IEEE shortest round-trip, matches SE19+/JDK21 Double.toString; oracle pinned to JDK21 since JDK17 FloatingDecimal is non-shortest)
- [x] boxing: Integer/Long/Double/Float/Boolean/Character/Short/Byte (valueOf/xxxValue/equals/hashCode/parse/toString); [ ] generic functional interfaces w/ box-unbox adaptation
      functional interfaces + autoboxing work
- [ ] remaining opcodes / edge cases surfaced by differential fuzzing
- [x] Character intrinsics (isDigit/isLetter/isWhitespace/case/digit/compare/getNumericValue) + String toUpperCase/toLowerCase/trim/strip/equalsIgnoreCase/indexOf(II)/lastIndexOf/repeat/toCharArray; [ ] more Math/Objects/Arrays
- [x] Comparator + Arrays.sort(T[], Comparator): lambda/method-ref comparators, stable, re-enters interpreter per compare; [ ] Collections.sort (needs List)
- [ ] keep GROWING scripts/differential.sh every iteration (it finds real bugs)

## Phase 1 — real java.base (COMMITTED DIRECTION, 2026-08-21)

Decision: bytecode-primary + intrinsic-accelerated (the HotSpot model). Jebena loads
the REAL java.base class files and executes their bytecode; hand-written Zig stays as
(a) genuine native methods and (b) speed intrinsics for hot, well-specified leaf
methods (Math, arraycopy, String hash/compare, parse/format). This is what every fast
production JVM does. Rationale: compatibility, reflection, and user-subclassing of
library types all require real classes; reimplementing all of java.base in Zig cannot
run real-world jars (Spring, JDBC).

Bootstrap policy (clean-room + licensing): DO NOT redistribute OpenJDK class files.
Jebena loads java.base from an installed JDK's module image at runtime (bring-your-own
-JDK). Our VM stays clean-room; the class library is the platform's. Revisit writing
our own java.base later if the thesis requires a fully from-scratch stack.

Concrete steps (each a loop iteration or few):
- [ ] classfile source: read .class bytes from a JDK module image (jimage/`lib/modules`)
      or an extracted dir; Loader falls back to it when a class isn't on the app path.
- [ ] native-method registry: ACC_NATIVE methods dispatch to Zig; repurpose existing
      intrinsics as the native impls (Object/System/Unsafe/Class primitives).
- [ ] bring up real java/lang/Object, then leaf classes with light <clinit>.
- [ ] real java/lang/String (byte[] value + coder, StringLatin1/StringUTF16 natives).
- [ ] System.initPhase1 / <clinit> dependency ordering for the core set.
- [ ] real boxing (Integer/Long/Double...), Number, Math (keep Zig intrinsics on top).
- [ ] real java.util core: AbstractCollection/List, ArrayList, HashMap, Arrays, Objects.
- [ ] keep every prior differential test green throughout (regression guard).

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
