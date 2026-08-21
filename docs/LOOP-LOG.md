# Loop Log

One line per completed iteration (newest at bottom).

2026-08-21 13:57  StringBuilder/StringBuffer (append chaining, toString, reverse, length, charAt)  (tests 107, diff 115)
2026-08-21 14:07  instance-capturing lambdas + bound/this method refs (obj::m, this-capture) + Objects.requireNonNull/isNull/nonNull  (tests 107, diff 119)
2026-08-21 14:14  boxing: Integer/Long/Double/Float/Boolean/Character/Short/Byte valueOf/xxxValue/equals/hashCode/parseInt/parseLong/toString (BoxedObj heap variant)  (tests 107, diff 128)
2026-08-21 14:32  float/double->String: IEEE shortest round-trip (Double/Float.toString, String.valueOf, string concat, boxed toString). Differential oracle moved to JDK21 (JDK17 legacy FloatingDecimal is non-shortest / non-SE-conformant)  (tests 107, diff 137)
2026-08-21 14:44  Comparator + Arrays.sort(T[], Comparator): lambda + static-method-ref comparators, stable insertion sort re-entering the interpreter per compare  (tests 107, diff 143)
