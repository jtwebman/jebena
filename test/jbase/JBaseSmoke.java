package jebena;

// Exercises Jebena's OWN clean-room java.base as real bytecode:
// java.lang.Object, java.lang.Math, and the Throwable/Exception hierarchy
// (real detailMessage field + super(message) constructor chain + getMessage).
public class JBaseSmoke {
    public static int demo() {
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
        return r;
    }
}
