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
        return r; // expect 10267
    }
}
