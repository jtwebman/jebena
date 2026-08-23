import java.util.Arrays;

public class DiffArrays3 {
    static int checksum(int[] a) {
        int acc = a.length;
        for (int i = 0; i < a.length; i++) {
            acc = acc * 31 + a[i];
        }
        return acc;
    }

    public static int rangeFill() {
        int[] a = new int[8];
        Arrays.fill(a, 7);
        Arrays.fill(a, 2, 6, -3);
        return checksum(a);
    }

    public static int rangeFillFull() {
        int[] a = new int[5];
        Arrays.fill(a, 0, 5, 9);
        return checksum(a);
    }

    public static int rangeFillEmptyRange() {
        int[] a = new int[4];
        Arrays.fill(a, 1);
        Arrays.fill(a, 2, 2, 99); // empty range: no change
        return checksum(a);
    }

    public static int rangeFillBadRange() {
        int[] a = new int[4];
        try {
            Arrays.fill(a, 3, 1, 5);
            return 0;
        } catch (IllegalArgumentException e) {
            return 111;
        }
    }

    public static int rangeFillOob() {
        int[] a = new int[4];
        try {
            Arrays.fill(a, 1, 9, 5);
            return 0;
        } catch (ArrayIndexOutOfBoundsException e) {
            return 222;
        }
    }

    public static int setAllSquares() {
        int[] a = new int[6];
        Arrays.setAll(a, x -> x * x);
        return checksum(a);
    }

    public static int equalsRangeMatch() {
        int[] a = {1, 2, 3, 4, 5, 6};
        int[] b = {9, 3, 4, 5, 0};
        boolean eq = Arrays.equals(a, 2, 5, b, 1, 4);
        return eq ? 1 : 0;
    }

    public static int equalsRangeDiffLength() {
        int[] a = {1, 2, 3, 4, 5};
        int[] b = {2, 3, 4};
        boolean eq = Arrays.equals(a, 1, 5, b, 0, 3);
        return eq ? 1 : 0;
    }

    public static int equalsRangeContentDiff() {
        int[] a = {1, 2, 3, 4};
        int[] b = {2, 9, 4};
        boolean eq = Arrays.equals(a, 1, 4, b, 0, 3);
        return eq ? 1 : 0;
    }

    public static int copyOfRangePad() {
        int[] a = {5, 6, 7};
        int[] r = Arrays.copyOfRange(a, 1, 6); // toIndex past length -> zero pad
        return checksum(r);
    }

    public static int intHashCode() {
        int[] a = {3, 1, 4, 1, 5, 9, 2, 6};
        return Arrays.hashCode(a);
    }

    public static int charFill() {
        char[] c = new char[6];
        Arrays.fill(c, 'x');
        Arrays.fill(c, 1, 4, 'A');
        int acc = c.length;
        for (int i = 0; i < c.length; i++) {
            acc = acc * 31 + c[i];
        }
        return acc;
    }

    public static int longFill() {
        long[] l = new long[5];
        Arrays.fill(l, 100L);
        Arrays.fill(l, 1, 3, 5000000000L);
        int acc = l.length;
        for (int i = 0; i < l.length; i++) {
            long v = l[i];
            acc = acc * 31 + (int) (v ^ (v >>> 32));
        }
        return acc;
    }

    public static int mismatchDiff() {
        int[] a = {1, 2, 3, 4, 5};
        int[] b = {1, 2, 9, 4, 5};
        return Arrays.mismatch(a, b);
    }

    public static int mismatchPrefix() {
        int[] a = {1, 2, 3};
        int[] b = {1, 2, 3, 4, 5};
        return Arrays.mismatch(a, b);
    }

    public static int mismatchEqual() {
        int[] a = {7, 8, 9};
        int[] b = {7, 8, 9};
        return Arrays.mismatch(a, b);
    }
}
