package jebena;

// Exercises Jebena's OWN clean-room java.lang.Object (loaded as real bytecode).
public class JBaseSmoke {
    public static int demo() {
        Object a = new Object();
        Object b = new Object();
        int r = 0;
        if (a.equals(a)) r += 1;    // reference-equal -> true
        if (!a.equals(b)) r += 10;  // distinct objects -> not equal
        if (a.hashCode() == a.hashCode()) r += 100; // identity hash is stable
        return r; // expect 111
    }
}
