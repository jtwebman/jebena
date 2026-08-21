package jebena;

// Exercises Jebena's OWN clean-room java.base (loaded as real bytecode):
// java.lang.Object (equals/hashCode/native identityHashCode) and java.lang.Math.
public class JBaseSmoke {
    public static int demo() {
        Object a = new Object();
        Object b = new Object();
        int r = 0;
        if (a.equals(a)) r += 1;    // reference-equal -> true
        if (!a.equals(b)) r += 10;  // distinct objects -> not equal
        if (a.hashCode() == a.hashCode()) r += 100; // identity hash is stable
        // Our clean-room Math runs as REAL bytecode (the Zig intrinsic defers to it).
        r += Math.abs(-7);          // 7
        r += Math.max(3, 9);        // 9
        r += Math.min(3, 9);        // 3
        // floorDiv/floorMod are NOT Zig intrinsics: succeeding here proves the
        // migration switch ran our real bytecode, not an intrinsic.
        r += Math.floorMod(-7, 3);  // 2
        r += Math.floorDiv(7, 2);   // 3
        return r; // expect 135
    }
}
