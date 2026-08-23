import java.util.Comparator;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.function.ToDoubleFunction;

public class DiffCmp2 {

    // Stable insertion sort using the given comparator only (null handling
    // is delegated entirely to the comparator).
    static void ssort(Object[] a, Comparator c) {
        for (int i = 1; i < a.length; i++) {
            Object key = a[i];
            int j = i - 1;
            while (j >= 0 && c.compare(a[j], key) > 0) {
                a[j + 1] = a[j];
                j--;
            }
            a[j + 1] = key;
        }
    }

    static int charSum(String s) {
        if (s == null) {
            return 7;
        }
        int t = 1;
        for (int i = 0; i < s.length(); i++) {
            t = t * 31 + s.charAt(i);
        }
        return t;
    }

    static int checksum(Object[] a) {
        int acc = 17;
        for (int i = 0; i < a.length; i++) {
            acc = acc * 131 + (i + 1) * charSum((String) a[i]);
        }
        return acc;
    }

    static String[] withNulls() {
        return new String[] { "banana", null, "apple", null, "cherry", "apple" };
    }

    public static int nullsFirst() {
        String[] a = withNulls();
        ssort(a, Comparator.nullsFirst(Comparator.naturalOrder()));
        return checksum(a);
    }

    public static int nullsLast() {
        String[] a = withNulls();
        ssort(a, Comparator.nullsLast(Comparator.naturalOrder()));
        return checksum(a);
    }

    public static int nullsFirstReversed() {
        String[] a = withNulls();
        ssort(a, Comparator.nullsFirst(Comparator.reverseOrder()));
        return checksum(a);
    }

    public static int nullsLastReversed() {
        String[] a = withNulls();
        ssort(a, Comparator.nullsLast(Comparator.reverseOrder()));
        return checksum(a);
    }

    public static int byLengthDesc() {
        String[] a = new String[] { "bb", "a", "cccc", "dd", "e", "fff" };
        Function lenKey = new Function() {
            public Object apply(Object o) {
                return Integer.valueOf(((String) o).length());
            }
        };
        ssort(a, Comparator.comparing(lenKey, Comparator.reverseOrder()));
        return checksum(a);
    }

    public static int byLengthAsc() {
        String[] a = new String[] { "bb", "a", "cccc", "dd", "e", "fff" };
        Function lenKey = new Function() {
            public Object apply(Object o) {
                return Integer.valueOf(((String) o).length());
            }
        };
        ssort(a, Comparator.comparing(lenKey, Comparator.naturalOrder()));
        return checksum(a);
    }

    // Records encoded as String so checksum machinery is reused; sort keys
    // are derived from the string content.
    static final class Rec {
        final String name;
        final long lkey;
        final double dkey;
        Rec(String name, long lkey, double dkey) {
            this.name = name;
            this.lkey = lkey;
            this.dkey = dkey;
        }
    }

    static int recChecksum(Object[] a) {
        int acc = 17;
        for (int i = 0; i < a.length; i++) {
            acc = acc * 131 + (i + 1) * charSum(((Rec) a[i]).name);
        }
        return acc;
    }

    public static int thenComparingLongTie() {
        Rec[] a = new Rec[] {
            new Rec("x", 30L, 0.0),
            new Rec("y", 10L, 0.0),
            new Rec("z", 10L, 0.0),
            new Rec("w", 20L, 0.0),
            new Rec("v", 10L, 0.0)
        };
        // primary: name length (all length 1 -> all tie), secondary: long key.
        Function lenKey = new Function() {
            public Object apply(Object o) {
                return Integer.valueOf(((Rec) o).name.length());
            }
        };
        ToLongFunction lk = new ToLongFunction() {
            public long applyAsLong(Object o) {
                return ((Rec) o).lkey;
            }
        };
        Comparator c = Comparator.comparing(lenKey, Comparator.naturalOrder())
                .thenComparingLong(lk);
        ssort(a, c);
        return recChecksum(a);
    }

    public static int thenComparingDoubleTie() {
        Rec[] a = new Rec[] {
            new Rec("x", 0L, 3.5),
            new Rec("y", 0L, 1.25),
            new Rec("z", 0L, 1.25),
            new Rec("w", 0L, 2.0),
            new Rec("v", 0L, 1.25)
        };
        Function lenKey = new Function() {
            public Object apply(Object o) {
                return Integer.valueOf(((Rec) o).name.length());
            }
        };
        ToDoubleFunction dk = new ToDoubleFunction() {
            public double applyAsDouble(Object o) {
                return ((Rec) o).dkey;
            }
        };
        Comparator c = Comparator.comparing(lenKey, Comparator.naturalOrder())
                .thenComparingDouble(dk);
        ssort(a, c);
        return recChecksum(a);
    }

    public static int allNulls() {
        String[] a = new String[] { null, null, null };
        ssort(a, Comparator.nullsFirst(Comparator.naturalOrder()));
        int acc = a.length;
        for (int i = 0; i < a.length; i++) {
            acc = acc * 3 + (a[i] == null ? 1 : 0);
        }
        return acc;
    }
}
