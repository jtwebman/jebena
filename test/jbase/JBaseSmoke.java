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
        return r;
    }
}
