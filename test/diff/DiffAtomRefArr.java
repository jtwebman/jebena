import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Differential coverage for java.util.concurrent.atomic.AtomicReferenceArray:
 * construction (all null), get/set, getAndSet (old value), compareAndSet with
 * == identity (success + failure), length, from-array copy semantics, and
 * toString. Each method returns a deterministic int checked byte-for-byte vs
 * real java.
 */
public class DiffAtomRefArr {
    public static int allNull() {
        AtomicReferenceArray a = new AtomicReferenceArray(3);
        int nulls = 0;
        for (int i = 0; i < a.length(); i++) {
            if (a.get(i) == null) {
                nulls++;
            }
        }
        return nulls; // 3
    }

    public static int setGet() {
        AtomicReferenceArray a = new AtomicReferenceArray(3);
        a.set(1, "hello");
        String s = (String) a.get(1);
        int acc = 0;
        for (int i = 0; i < s.length(); i++) {
            acc += s.charAt(i);
        }
        return acc; // checksum of "hello"
    }

    public static int getAndSetOld() {
        AtomicReferenceArray a = new AtomicReferenceArray(3);
        a.set(0, "old");
        String old = (String) a.getAndSet(0, "new");
        int acc = 0;
        for (int i = 0; i < old.length(); i++) {
            acc += old.charAt(i);
        }
        return acc; // checksum of "old"
    }

    public static int casSuccessIdentity() {
        AtomicReferenceArray a = new AtomicReferenceArray(3);
        String ref = "token";
        a.set(2, ref);
        return a.compareAndSet(2, ref, "next") ? 1 : 0; // 1
    }

    public static int casFailIdentity() {
        AtomicReferenceArray a = new AtomicReferenceArray(3);
        String ref = "dup";
        // Build an equal-content String with a distinct identity (not interned).
        String other = new StringBuilder().append("du").append("p").toString();
        a.set(2, ref);
        return a.compareAndSet(2, other, "next") ? 1 : 0; // 0 (identity differs)
    }

    public static int casNullExpect() {
        AtomicReferenceArray a = new AtomicReferenceArray(3);
        boolean ok = a.compareAndSet(0, null, "filled"); // slot starts null
        return (ok && "filled".equals(a.get(0))) ? 1 : 0; // 1
    }

    public static int lengthCase() {
        return new AtomicReferenceArray(new Object[]{"a", "b", "c", "d"}).length(); // 4
    }

    public static int fromArrayCopy() {
        Object[] src = {"x", "y", "z"};
        AtomicReferenceArray a = new AtomicReferenceArray(src);
        src[0] = "MUTATED"; // must not affect the atomic array (copy of refs)
        String v = (String) a.get(0);
        int acc = 0;
        for (int i = 0; i < v.length(); i++) {
            acc += v.charAt(i);
        }
        return acc * 10 + a.length(); // checksum("x")*10 + 3
    }

    public static int toStr() {
        AtomicReferenceArray a = new AtomicReferenceArray(3);
        a.set(0, "aa");
        a.set(1, "bb");
        a.set(2, "cc");
        String s = a.toString(); // "[aa, bb, cc]"
        int acc = 0;
        for (int i = 0; i < s.length(); i++) {
            acc += s.charAt(i);
        }
        return acc;
    }

    public static int toStrWithNull() {
        AtomicReferenceArray a = new AtomicReferenceArray(2);
        a.set(0, "hi");
        String s = a.toString(); // "[hi, null]"
        int acc = 0;
        for (int i = 0; i < s.length(); i++) {
            acc += s.charAt(i);
        }
        return acc;
    }
}
