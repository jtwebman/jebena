import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Comparator;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.function.ToDoubleFunction;
import java.util.function.Function;

public class DiffCmp {

    static List ints(int[] vs) {
        List xs = new ArrayList();
        for (int i = 0; i < vs.length; i++) {
            xs.add(Integer.valueOf(vs[i]));
        }
        return xs;
    }

    static List strs(String[] vs) {
        List xs = new ArrayList();
        for (int i = 0; i < vs.length; i++) {
            xs.add(vs[i]);
        }
        return xs;
    }

    static int foldInts(List xs) {
        int acc = 0;
        for (int i = 0; i < xs.size(); i++) {
            acc = acc * 31 + ((Integer) xs.get(i)).intValue();
        }
        return acc;
    }

    static int foldStrs(List xs) {
        int acc = 0;
        for (int i = 0; i < xs.size(); i++) {
            String s = (String) xs.get(i);
            for (int j = 0; j < s.length(); j++) {
                acc = acc * 31 + s.charAt(j);
            }
            acc = acc * 31 + 255;
        }
        return acc;
    }

    public static int natural() {
        List xs = ints(new int[] { 5, -3, 0, 42, -100, 7, 5 });
        Collections.sort(xs, Comparator.naturalOrder());
        return foldInts(xs);
    }

    public static int reverse() {
        List xs = ints(new int[] { 5, -3, 0, 42, -100, 7, 5 });
        Collections.sort(xs, Comparator.reverseOrder());
        return foldInts(xs);
    }

    public static int reversedOfNatural() {
        List xs = ints(new int[] { 5, -3, 0, 42, -100, 7, 5 });
        Collections.sort(xs, Comparator.naturalOrder().reversed());
        return foldInts(xs);
    }

    public static int byAbs() {
        List xs = ints(new int[] { 5, -3, 0, 42, -100, 7, -5 });
        Collections.sort(xs, Comparator.comparingInt(new ToIntFunction() {
            public int applyAsInt(Object v) {
                int x = ((Integer) v).intValue();
                return x < 0 ? -x : x;
            }
        }));
        return foldInts(xs);
    }

    public static int byLength() {
        List xs = strs(new String[] { "bbb", "a", "cc", "dddd", "ee", "f" });
        Collections.sort(xs, Comparator.comparing(new Function() {
            public Object apply(Object v) {
                return Integer.valueOf(((String) v).length());
            }
        }));
        return foldStrs(xs);
    }

    public static int byLengthThenNatural() {
        List xs = strs(new String[] { "bb", "aa", "z", "cc", "a", "bbb" });
        Comparator byLen = Comparator.comparingInt(new ToIntFunction() {
            public int applyAsInt(Object v) {
                return ((String) v).length();
            }
        });
        Collections.sort(xs, byLen.thenComparing(Comparator.naturalOrder()));
        return foldStrs(xs);
    }

    public static int thenComparingIntCase() {
        List xs = strs(new String[] { "bb", "aa", "z", "cc", "a", "bbb" });
        Comparator byNat = Comparator.naturalOrder();
        Collections.sort(xs, byNat.thenComparingInt(new ToIntFunction() {
            public int applyAsInt(Object v) {
                return ((String) v).length();
            }
        }));
        return foldStrs(xs);
    }

    public static int byLong() {
        List xs = ints(new int[] { 5, -3, 0, 42, -100, 7 });
        Collections.sort(xs, Comparator.comparingLong(new ToLongFunction() {
            public long applyAsLong(Object v) {
                return (long) ((Integer) v).intValue() * 1000L;
            }
        }));
        return foldInts(xs);
    }

    public static int byDouble() {
        List xs = ints(new int[] { 5, -3, 0, 42, -100, 7 });
        Collections.sort(xs, Comparator.comparingDouble(new ToDoubleFunction() {
            public double applyAsDouble(Object v) {
                return -(double) ((Integer) v).intValue();
            }
        }));
        return foldInts(xs);
    }

    public static int emptyNatural() {
        List xs = ints(new int[] {});
        Collections.sort(xs, Comparator.naturalOrder());
        return foldInts(xs) + 1;
    }
}
