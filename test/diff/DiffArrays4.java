import java.util.Arrays;
import java.util.function.IntToLongFunction;
import java.util.function.IntToDoubleFunction;

public class DiffArrays4 {

    public static int setAllLongSum() {
        long[] a = new long[6];
        Arrays.setAll(a, new IntToLongFunction() {
            public long applyAsLong(int i) {
                return (long) i * 1000000L + 7L;
            }
        });
        long sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i];
        }
        return (int) (sum % 1000000007L);
    }

    public static int setAllDoubleChecksum() {
        double[] a = new double[8];
        Arrays.setAll(a, new IntToDoubleFunction() {
            public double applyAsDouble(int i) {
                return i * 1.5 + 0.25;
            }
        });
        int acc = 0;
        for (int i = 0; i < a.length; i++) {
            acc = acc * 31 + (int) (a[i] * 4.0);
        }
        return acc;
    }

    public static int fillDoubleRangeChecksum() {
        double[] a = new double[10];
        Arrays.fill(a, 2, 7, 3.5);
        int acc = 0;
        for (int i = 0; i < a.length; i++) {
            acc = acc * 31 + (int) (a[i] * 2.0);
        }
        return acc;
    }

    public static int hashCodeStringArray() {
        String[] s = new String[] { "alpha", "beta", "gamma", "delta" };
        return Arrays.hashCode(s);
    }

    public static int hashCodeStringArrayWithNull() {
        String[] s = new String[] { "x", null, "y" };
        return Arrays.hashCode(s);
    }

    public static int hashCodeLongArray() {
        long[] a = new long[] { 1L, 1234567890123L, -42L, 0L };
        return Arrays.hashCode(a);
    }

    public static int equalsLongArrays() {
        long[] a = new long[] { 5L, 6L, 7L, 8L };
        long[] b = new long[] { 5L, 6L, 7L, 8L };
        long[] c = new long[] { 5L, 6L, 7L, 9L };
        long[] d = new long[] { 5L, 6L, 7L };
        int acc = 0;
        acc = acc * 10 + (Arrays.equals(a, b) ? 1 : 0);
        acc = acc * 10 + (Arrays.equals(a, c) ? 1 : 0);
        acc = acc * 10 + (Arrays.equals(a, d) ? 1 : 0);
        acc = acc * 10 + (Arrays.equals(a, a) ? 1 : 0);
        return acc;
    }

    public static int fillBooleanCountTrues() {
        boolean[] a = new boolean[9];
        Arrays.fill(a, true);
        int trues = 0;
        for (int i = 0; i < a.length; i++) {
            if (a[i]) trues++;
        }
        return trues;
    }

    public static int copyOfIntGrowShrink() {
        int[] a = new int[] { 3, 1, 4, 1, 5, 9 };
        int[] grow = Arrays.copyOf(a, 9);
        int[] shrink = Arrays.copyOf(a, 3);
        int acc = 0;
        for (int i = 0; i < grow.length; i++) {
            acc = acc * 31 + grow[i];
        }
        for (int i = 0; i < shrink.length; i++) {
            acc = acc * 31 + shrink[i];
        }
        return acc;
    }
}
