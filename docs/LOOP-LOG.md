# Loop Log

One line per completed iteration (newest at bottom).

2026-08-21 13:57  StringBuilder/StringBuffer (append chaining, toString, reverse, length, charAt)  (tests 107, diff 115)
2026-08-21 14:07  instance-capturing lambdas + bound/this method refs (obj::m, this-capture) + Objects.requireNonNull/isNull/nonNull  (tests 107, diff 119)
2026-08-21 14:14  boxing: Integer/Long/Double/Float/Boolean/Character/Short/Byte valueOf/xxxValue/equals/hashCode/parseInt/parseLong/toString (BoxedObj heap variant)  (tests 107, diff 128)
2026-08-21 14:32  float/double->String: IEEE shortest round-trip (Double/Float.toString, String.valueOf, string concat, boxed toString). Differential oracle moved to JDK21 (JDK17 legacy FloatingDecimal is non-shortest / non-SE-conformant)  (tests 107, diff 137)
2026-08-21 14:44  Comparator + Arrays.sort(T[], Comparator): lambda + static-method-ref comparators, stable insertion sort re-entering the interpreter per compare  (tests 107, diff 143)
2026-08-21 14:52  Character intrinsics + String batch (toUpperCase/toLowerCase/trim/strip/equalsIgnoreCase/indexOf(II)/lastIndexOf/repeat/toCharArray)  (tests 107, diff 152)
2026-08-21 15:04  Phase 1 begins: own clean-room java.base. jbase/ + build-jbase.sh (javac --patch-module), real java/lang/Object overrides the stub, native identityHashCode seed, jbase-smoke proves our compiled Object runs as real bytecode  (tests 107, diff 152, smoke 111)
2026-08-21 15:16  Generalize native-method dispatch: ACC_NATIVE flag on Method, nativeInvoke registry keyed by (owner,name,desc), invokeStatic/invokeInstance route native methods to it; identityHashCode now flows through the registry via real resolution  (tests 107, diff 152, smoke 111)
2026-08-21 15:30  Migration switch: Class.is_stub gates the Zig intrinsic layer so a loaded real class runs its own bytecode/native; brought up clean-room java/lang/Math (abs/max/min/floorDiv/floorMod) — floorDiv/floorMod have no intrinsic, proving real bytecode executes  (tests 107, diff 152, smoke 135)
2026-08-21 15:44  Clean-room exception hierarchy as real bytecode: Throwable (detailMessage field + getMessage/getLocalizedMessage) + Exception/RuntimeException/IllegalArgumentException with super(message) constructor chains; throw/catch subtype matching over our own classes  (tests 107, diff 152, smoke 209)
2026-08-21 15:57  Native double Math via the registry: jbase Math declares sqrt/cbrt/floor/ceil/abs/pow native; nativeInvoke implements them with the same Zig math as the intrinsic (matches oracle)  (tests 107, diff 152, smoke 1267)
2026-08-21 16:10  Complete clean-room exception hierarchy: Error + NullPointerException/ArithmeticException/ClassCastException/IllegalStateException/IndexOutOfBoundsException/ArrayIndexOutOfBoundsException/NegativeArraySizeException/StringIndexOutOfBoundsException as real classes; VM raise() instantiates them, caught with subtype matching  (tests 107, diff 152, smoke 10267)
2026-08-21 16:30  Begin String migration (the hard one): clean-room java.lang.String backed by char[]; ldc string literals build real String instances (representation-aware createString); length/isEmpty/charAt(bounds-checked)/hashCode/equals as real bytecode over value[]  (tests 107, diff 152, smoke 90373)
