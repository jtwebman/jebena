package jebena;

// Exercises Jebena's OWN clean-room java.base as real bytecode:
// java.lang.Object, java.lang.Math, and the Throwable/Exception hierarchy
// (real detailMessage field + super(message) constructor chain + getMessage).
public class JBaseSmoke {
    public static int demo() throws Exception {
        Object a = new Object();
        Object b = new Object();
        int r = 0;
        if (a.equals(a)) r += 1;
        if (!a.equals(b)) r += 10;
        if (a.hashCode() == a.hashCode()) r += 100;
        r += Math.abs(-7);          // 7
        r += Math.max(3, 9);        // 9
        r += Math.min(3, 9);        // 3
        r += Math.floorMod(-7, 3);  // 2
        r += Math.floorDiv(7, 2);   // 3   (subtotal 135)

        // Real exceptions: constructor chain stores detailMessage, getMessage reads it.
        try {
            throw new RuntimeException("boom");
        } catch (RuntimeException e) {
            r += e.getMessage().length();        // "boom" -> 4
        }
        try {
            throw new IllegalArgumentException("bad-arg");
        } catch (Exception e) {                  // caught as supertype
            r += e.getMessage().length() * 10;   // "bad-arg" -> 7 -> 70
        }
        // Native double Math (declared native in jbase Math, run via the Zig registry).
        r += (int) Math.sqrt(144.0);   // 12
        r += (int) Math.floor(9.7);    // 9
        r += (int) Math.ceil(9.2);     // 10
        r += (int) Math.pow(2.0, 10.0); // 1024
        r += (int) Math.abs(-3.5);     // 3

        // VM-thrown exceptions caught as our OWN clean-room exception classes.
        try {
            int q = 7 / 0;
        } catch (ArithmeticException e) {
            r += 3000;
        }
        try {
            int[] arr = new int[2];
            int y = arr[5];
        } catch (ArrayIndexOutOfBoundsException e) {
            r += 2000;
        }
        try {
            int[] arr = new int[2];
            int y = arr[5];
        } catch (IndexOutOfBoundsException e) { // supertype catch of AIOOBE
            r += 4000;
        }

        // Real clean-room java.lang.String: char[]-backed, methods as bytecode.
        String str = "hello";
        r += str.length();                              // 5
        r += str.charAt(1);                             // 'e' = 101
        if (str.equals("hello")) r += 10000;            // content equal
        if (!str.equals("world")) r += 20000;           // not equal
        if (str.hashCode() == "hello".hashCode()) r += 50000; // stable hash
        // Producers: real String from concat/substring/indexOf and invokedynamic +.
        r += str.concat("!").length();          // "hello!" -> 6
        r += (("foo" + "bar")).length();        // invokedynamic concat -> "foobar" -> 6
        r += ("x" + 5 + "y").length();          // mixed concat -> "x5y" -> 3
        r += "hello".substring(1, 3).length();  // "el" -> 2
        r += "hello".indexOf('l');              // 2
        if ("hello".startsWith("he")) r += 300000;
        r += "abc".compareTo("abd");            // -1
        // String literal interning: equal literals are ==, runtime concat is not.
        if ("abc" == "abc") r += 700000;
        String p1 = "wxyz";
        String p2 = "wxyz";
        if (p1 == p2) r += 800000;
        String xv = "x";
        if ((xv + "y") != "xy") r += 5; // runtime concat -> fresh, not interned

        // Remaining String methods (each auto-validated against real java).
        r += "HeLLo".toUpperCase().length();       // "HELLO" -> 5
        r += "HeLLo".toLowerCase().charAt(0);      // 'h' -> 104
        if ("Hello".equalsIgnoreCase("hello")) r += 900;
        r += "  hi  ".trim().length();             // 2
        r += String.valueOf(12345).length();       // "12345" -> 5
        r += String.valueOf(true).length();        // "true" -> 4
        r += String.valueOf(3.5).length();         // "3.5" -> 3
        r += "banana".lastIndexOf('a');            // 5
        if ("test.java".endsWith(".java")) r += 42;
        r += "abcabc".replace('a', 'x').indexOf('x'); // 0
        r += "hello world".indexOf("world");       // 6
        r += String.valueOf('Z').charAt(0);        // 'Z' -> 90

        // Boxing: real Integer instances, valueOf cache identity, autobox/unbox.
        Integer ia = 127;  // autobox -> Integer.valueOf(127) (cached)
        Integer ib = 127;
        Integer ic = 128;  // not cached
        Integer id2 = 128;
        if (ia == ib) r += 1000000;   // cached -> same instance
        if (ic != id2) r += 2000000;  // uncached -> distinct instances
        if (ia.equals(ib)) r += 3000;
        r += ia.intValue();           // 127
        r += ic + id2;                // unbox both -> 256
        r += Integer.parseInt("4567");
        r += Integer.valueOf(99).intValue();
        r += Integer.valueOf(5).compareTo(Integer.valueOf(9)); // -1
        r += Integer.toString(789).length();  // 3

        // All boxed types: autobox/unbox, cache identity, equals/hashCode/toString.
        Long la = 100L;
        Long lb = 100L;   // cached
        Long lc = 1000L;
        Long ld = 1000L;  // not cached
        if (la == lb) r += 10000000;
        if (lc != ld) r += 20000000;
        r += (int) (la.longValue() + Long.parseLong("23"));  // 123
        r += Long.valueOf(7L).compareTo(Long.valueOf(3L));   // 1

        Double da = 3.5;
        Double db = 3.5;
        if (da.equals(db)) r += 5000;
        r += da.intValue();               // 3
        r += (int) (da.doubleValue() * 2); // 7
        r += Double.compare(1.0, 2.0);    // -1

        Boolean ba = true;   // valueOf -> TRUE singleton
        Boolean bb = true;
        if (ba == bb) r += 40000000;      // cached singleton identity
        if (ba.booleanValue()) r += 7;
        r += Boolean.valueOf("TRUE").hashCode(); // 1231

        Character ca = 'A';  // cached
        Character cb = 'A';
        if (ca == cb) r += 80000000;
        r += ca.charValue();              // 65
        r += Character.valueOf('z').compareTo(Character.valueOf('a')); // 25

        Short sa = (short) 50;
        r += sa.intValue();               // 50
        Byte ya = (byte) 9;
        r += ya.intValue();               // 9
        Float fa = 2.5f;
        r += (int) (fa.floatValue() * 4); // 10
        if (fa.equals(Float.valueOf(2.5f))) r += 600;

        // getClass() + Class + real toString (hash-independent parts only).
        r += "hello".getClass().getName().length();       // "java.lang.String" -> 16
        r += "hello".getClass().getSimpleName().length();  // "String" -> 6
        if ("a".getClass() == "b".getClass()) r += 111;    // one mirror per class
        Integer bx = 5;
        r += bx.getClass().getName().length();             // "java.lang.Integer" -> 17
        r += Integer.toHexString(255).length();            // "ff" -> 2
        if (Integer.toHexString(255).equals("ff")) r += 9;
        r += Integer.toHexString(-1).length();             // "ffffffff" -> 8
        if (Integer.toBinaryString(5).equals("101")) r += 13;
        RuntimeException ex = new RuntimeException("boom");
        if (ex.toString().equals("java.lang.RuntimeException: boom")) r += 17;
        NumberFormatException nfe = new NumberFormatException("bad");
        if (nfe.toString().equals("java.lang.NumberFormatException: bad")) r += 19;

        // java.util.ArrayList: autobox into Object[], growth past capacity, iterate.
        java.util.ArrayList<Integer> list = new java.util.ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            list.add(i);          // autobox -> Integer.valueOf(i); grows past 10
        }
        r += list.size();          // 20
        int sum = 0;
        for (int x : list) {       // enhanced-for -> Iterator; auto-unbox
            sum += x;
        }
        r += sum;                  // 210
        r += list.get(5);          // 6
        r += list.indexOf(15);     // 14
        if (list.contains(10)) r += 500;
        list.set(0, 100);
        r += list.get(0);          // 100
        r += list.remove(19);      // remove index 19 (value 20) -> 20
        r += list.size();          // 19
        if (java.util.Objects.equals(list.get(1), Integer.valueOf(2))) r += 700;
        r += list.toString().length();

        // java.util.HashMap: put/get many keys (resize), overwrite, remove, contains.
        java.util.HashMap<Integer, Integer> m = new java.util.HashMap<>();
        for (int i = 0; i < 50; i++) {
            m.put(i, i * i);   // autobox key+value; grows past threshold
        }
        r += m.size();          // 50
        int vsum = 0;
        for (int i = 0; i < 50; i++) {
            vsum += m.get(i);   // unbox
        }
        r += vsum % 100000;     // sum of squares 0..49
        if (m.containsKey(25)) r += 300;
        if (!m.containsKey(999)) r += 400;
        r += m.put(10, -1);     // returns old 100
        r += m.get(10);         // -1
        r += m.remove(20);      // returns 400
        r += m.size();          // 49
        if (m.containsValue(9)) r += 55;   // 3*3

        java.util.HashMap<String, Integer> sm = new java.util.HashMap<>();
        sm.put("apple", 1);
        sm.put("banana", 2);
        sm.put("cherry", 3);
        r += sm.get("banana");  // 2
        if (sm.containsKey("apple")) r += 11;
        r += sm.size();         // 3

        // java.util.HashSet
        java.util.HashSet<Integer> set = new java.util.HashSet<>();
        for (int i = 0; i < 30; i++) {
            set.add(i % 10);    // only 0..9 unique
        }
        r += set.size();        // 10
        if (set.contains(5)) r += 22;
        if (!set.contains(50)) r += 33;

        // Polymorphic declared types (interface dispatch) + Map views.
        java.util.List<Integer> pl = new java.util.ArrayList<>();
        pl.add(3);
        pl.add(1);
        pl.add(2);
        int psum = 0;
        for (int x : pl) {          // List iterator via interface
            psum += x;
        }
        r += psum;                   // 6
        r += pl.get(1);              // 1
        r += pl.size();              // 3

        java.util.Set<String> ps = new java.util.HashSet<>();
        ps.add("x");
        ps.add("y");
        ps.add("x");
        r += ps.size();              // 2
        if (ps.contains("y")) r += 9;

        java.util.Map<String, Integer> pm = new java.util.HashMap<>();
        pm.put("a", 10);
        pm.put("b", 20);
        pm.put("c", 30);
        r += pm.get("b");            // 20
        r += pm.size();              // 3
        int klen = 0;
        for (Object k : pm.keySet()) {
            klen += ((String) k).length();
        }
        r += klen;                   // 3
        int vs = 0;
        for (Object v : pm.values()) {
            vs += (Integer) v;
        }
        r += vs;                     // 60
        int esum = 0;
        for (java.util.Map.Entry<String, Integer> e : pm.entrySet()) {
            esum += e.getValue();
            esum += e.getKey().length();
        }
        r += esum;                   // 63

        // Real StringBuilder (char[]-backed): chaining, all append overloads.
        StringBuilder sb = new StringBuilder();
        sb.append("Hello").append(", ").append("World").append('!').append(' ')
          .append(42).append(' ').append(true).append(' ').append(3.5).append(' ').append(7L);
        String built = sb.toString();
        r += built.length();
        if (built.equals("Hello, World! 42 true 3.5 7")) r += 1234;
        r += sb.length();
        StringBuilder rev = new StringBuilder("abcdef");
        rev.reverse();
        if (rev.toString().equals("fedcba")) r += 77;
        StringBuilder ins = new StringBuilder("abc");
        ins.insert(1, "XY");            // aXYbc
        r += ins.toString().length();   // 5
        r += ins.charAt(1);             // 'X' = 88
        ins.deleteCharAt(0);            // XYbc
        r += ins.length();              // 4
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            big.append(i).append(',');  // growth stress
        }
        r += big.length();

        // Comparable + Comparator + Collections.
        java.util.List<Integer> nums = new java.util.ArrayList<>();
        int[] seed = { 5, 2, 8, 1, 9, 3, 7 };
        for (int v : seed) {
            nums.add(v);
        }
        java.util.Collections.sort(nums);          // natural ordering (Comparable)
        int packed = 0;
        for (int x : nums) {
            packed = packed * 10 + x;              // 1235789
        }
        r += packed % 1000000;
        java.util.Collections.sort(nums, (p, q) -> q - p); // reverse via Comparator
        r += nums.get(0);                          // 9
        java.util.Collections.reverse(nums);       // ascending again
        r += nums.get(0);                          // 1
        r += java.util.Collections.max(nums);      // 9
        r += java.util.Collections.min(nums);      // 1

        java.util.List<String> strs = new java.util.ArrayList<>();
        strs.add("banana");
        strs.add("apple");
        strs.add("cherry");
        java.util.Collections.sort(strs);          // natural String ordering
        r += strs.get(0).charAt(0);                // 'a' = 97
        r += java.util.Collections.max(strs).charAt(0); // 'c' = 99

        // Real java.util.Arrays.
        int[] arr = { 5, 3, 8, 1, 9, 2 };
        java.util.Arrays.sort(arr);
        r += java.util.Arrays.toString(arr).length();     // "[1, 2, 3, 5, 8, 9]" len
        r += java.util.Arrays.binarySearch(arr, 8);       // index of 8 in sorted -> 4
        int[] copy = java.util.Arrays.copyOf(arr, 8);
        r += copy.length;                                 // 8
        r += java.util.Arrays.copyOfRange(arr, 1, 4).length; // 3
        r += java.util.Arrays.hashCode(arr) % 100000;
        if (java.util.Arrays.equals(arr, java.util.Arrays.copyOf(arr, 6))) r += 321;
        int[] filled = new int[4];
        java.util.Arrays.fill(filled, 7);
        r += java.util.Arrays.toString(filled).length();  // "[7, 7, 7, 7]" len
        Integer[] objs = { 30, 10, 20 };
        java.util.Arrays.sort(objs);
        r += objs[0];                                       // 10
        java.util.List<Integer> alist = java.util.Arrays.asList(objs);
        r += alist.size();                                   // 3
        r += java.util.Arrays.toString(objs).length();      // "[10, 20, 30]" len

        // LinkedList as List + Deque.
        java.util.LinkedList<Integer> ll = new java.util.LinkedList<>();
        ll.add(1);
        ll.add(2);
        ll.add(3);
        r += ll.size();               // 3
        r += ll.get(1);               // 2
        ll.addFirst(0);               // [0,1,2,3]
        ll.addLast(4);                // [0,1,2,3,4]
        r += ll.getFirst() + ll.getLast(); // 4
        r += ll.removeFirst();        // 0
        r += ll.removeLast();         // 4 -> [1,2,3]
        ll.push(9);                   // [9,1,2,3]
        r += ll.pop();                // 9 -> [1,2,3]
        int lsum = 0;
        for (int o : ll) {
            lsum += o;
        }
        r += lsum;                    // 6
        r += ll.toString().length();  // "[1, 2, 3]" -> 9
        java.util.Deque<Integer> dq = new java.util.LinkedList<>();
        dq.offer(10);
        dq.offer(20);
        dq.push(5);                   // [5,10,20]
        r += dq.poll();               // 5
        r += dq.peek();               // 10
        r += dq.pollLast();           // 20
        r += dq.size();               // 1

        // TreeMap: sorted iteration (via standard keySet) + navigation.
        java.util.TreeMap<Integer, Integer> tm = new java.util.TreeMap<>();
        int[] tkeys = { 5, 2, 8, 1, 9, 3 };
        for (int k : tkeys) {
            tm.put(k, k * 10);
        }
        StringBuilder tsb = new StringBuilder();
        for (int k : tm.keySet()) {
            tsb.append(k).append(',');   // sorted: 1,2,3,5,8,9,
        }
        r += tsb.toString().length();
        r += tm.firstKey();              // 1
        r += tm.lastKey();               // 9
        r += tm.get(8);                  // 80
        java.util.NavigableMap<Integer, Integer> nm = tm;
        r += nm.ceilingKey(4);           // 5
        r += nm.floorKey(4);             // 3
        r += nm.higherKey(5);            // 8
        r += nm.lowerKey(5);             // 3
        tm.remove(5);
        r += tm.size();                  // 5
        r += tm.containsKey(5) ? 0 : 100;
        int tvs = 0;
        for (Object v : tm.values()) {
            tvs += (Integer) v;          // 10+20+30+80+90 = 230
        }
        r += tvs;

        // TreeSet: sorted, dedup.
        java.util.TreeSet<Integer> ts = new java.util.TreeSet<>();
        int[] svals = { 30, 10, 50, 20, 40, 10 };
        for (int v : svals) {
            ts.add(v);
        }
        r += ts.size();                  // 5
        r += ts.first();                 // 10
        r += ts.last();                  // 50
        r += ts.ceiling(25);             // 30
        int tssum = 0;
        for (int o : ts) {
            tssum += o;                  // 150
        }
        r += tssum;

        // === Dynamic proxy: InvocationHandler routes interface calls ===
        java.lang.reflect.InvocationHandler handler = (proxy, method, args) -> {
            if (method.getName().equals("add")) {
                return (Integer) args[0] + (Integer) args[1];
            }
            if (method.getName().equals("name")) {
                return "proxy-calc";
            }
            return null;
        };
        Calculator calc = (Calculator) java.lang.reflect.Proxy.newProxyInstance(
                Calculator.class.getClassLoader(), new Class[] { Calculator.class }, handler);
        r += calc.add(20, 22);        // 42 via handler
        r += calc.name().length();    // "proxy-calc" -> 10

        // === Atomics ===
        java.util.concurrent.atomic.AtomicInteger ai = new java.util.concurrent.atomic.AtomicInteger(10);
        r += ai.incrementAndGet();    // 11
        r += ai.getAndAdd(5);         // returns 11, value -> 16
        r += ai.get();                // 16
        java.util.concurrent.atomic.AtomicLong al = new java.util.concurrent.atomic.AtomicLong(100L);
        r += (int) al.addAndGet(23L); // 123

        // === Annotations: presence via RuntimeVisibleAnnotations decode ===
        Annotated anx = new Annotated();
        Class<?> ac = anx.getClass();
        if (ac.isAnnotationPresent(MyAnno.class)) r += 1000;
        if (!ac.isAnnotationPresent(OtherAnno.class)) r += 2000;
        MyAnno ma = (MyAnno) ac.getAnnotation(MyAnno.class);
        if (ma.value().equals("hello")) r += 3000;
        r += ma.num();                    // 42
        if (ac.getAnnotation(OtherAnno.class) == null) r += 4000;

        // === Reflection: getClass -> methods/fields/ctors, invoke/get/set/newInstance ===
        ReflectTarget rt0 = new ReflectTarget(5, "hi");
        Class<?> rc = rt0.getClass();
        java.lang.reflect.Method[] ms = rc.getDeclaredMethods();
        r += ms.length;   // addTo, square, describe = 3
        for (java.lang.reflect.Method mth : ms) {
            if (mth.getName().equals("square")) {
                r += (Integer) mth.invoke(null, new Object[] { 7 });   // static -> 49
            }
            if (mth.getName().equals("addTo")) {
                r += (Integer) mth.invoke(rt0, new Object[] { 10 });   // 5+10 = 15
            }
        }
        java.lang.reflect.Field[] fs = rc.getDeclaredFields();
        r += fs.length;   // x, label = 2
        for (java.lang.reflect.Field fld : fs) {
            if (fld.getName().equals("x")) {
                r += (Integer) fld.get(rt0);  // 5
                fld.set(rt0, 99);
            }
        }
        r += rt0.x;       // 99 (set worked)
        for (java.lang.reflect.Constructor ctorM : rc.getDeclaredConstructors()) {
            if (ctorM.getParameterCount() == 2) {
                ReflectTarget made = (ReflectTarget) ctorM.newInstance(new Object[] { 42, "made" });
                r += made.x;                   // 42
                r += made.describe().length(); // "made:42" -> 7
            }
        }

        // === Parallel batch: ArrayDeque, LinkedHashMap, PriorityQueue, Random, function ===
        java.util.ArrayDeque<Integer> adq = new java.util.ArrayDeque<>();
        adq.push(1);
        adq.push(2);      // front: [2,1]
        adq.offer(3);     // tail:  [2,1,3]
        r += adq.pop();   // 2
        r += adq.pollLast(); // 3
        r += adq.size();  // 1

        java.util.LinkedHashMap<String, Integer> lhm = new java.util.LinkedHashMap<>();
        lhm.put("c", 3);
        lhm.put("a", 1);
        lhm.put("b", 2);
        lhm.put("a", 10); // update, keeps position
        StringBuilder lo = new StringBuilder();
        for (String k : lhm.keySet()) {
            lo.append(k);  // insertion order: "cab"
        }
        if (lo.toString().equals("cab")) r += 50;
        r += lhm.get("a"); // 10

        java.util.PriorityQueue<Integer> pq = new java.util.PriorityQueue<>();
        pq.offer(5);
        pq.offer(1);
        pq.offer(3);
        pq.offer(2);
        pq.offer(4);
        StringBuilder po = new StringBuilder();
        while (!pq.isEmpty()) {
            po.append(pq.poll());  // min-heap order: "12345"
        }
        if (po.toString().equals("12345")) r += 500;

        // Random: exact LCG must match OpenJDK for a fixed seed.
        java.util.Random rnd = new java.util.Random(42);
        int rsum = 0;
        for (int i = 0; i < 10; i++) {
            rsum += rnd.nextInt(1000);
        }
        r += rsum;
        r += (int) (new java.util.Random(12345).nextLong() % 100000);

        // Functional interfaces as lambda targets.
        java.util.function.Function<Integer, Integer> fn = x -> x * 2;
        r += fn.apply(21);       // 42
        java.util.function.Predicate<Integer> pr = x -> x > 5;
        r += pr.test(10) ? 7 : 0;
        java.util.function.Supplier<Integer> sup = () -> 99;
        r += sup.get();          // 99
        return r;
    }
}
