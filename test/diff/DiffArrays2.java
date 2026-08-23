import java.util.Arrays;

/**
 * Differential coverage for the java.util.Arrays "deep" and hash additions:
 * deepToString, deepEquals (equal + unequal), deepHashCode, hashCode(long[]),
 * hashCode(Object[]), and stream(int[]). String results are reduced to a
 * rolling char checksum; each method returns a deterministic int checked
 * byte-for-byte vs real java.
 *
 * The *Nested cases exercise deep dispatch over element arrays; jebena models
 * arrays without a recoverable component type, so those diverge (see notes).
 */
public class DiffArrays2 {

    private static int checksum(String s) {
        int h = 0;
        for (int i = 0; i < s.length(); i++) {
            h = h * 31 + s.charAt(i);
        }
        return h;
    }

    public static int deepToStringFlat() {
        Object[] a = new Object[]{"a", "b", null, "c"};
        return checksum(Arrays.deepToString(a)); // "[a, b, null, c]"
    }

    public static int deepEqualsFlatEqual() {
        Object[] a = new Object[]{"x", "y", "z"};
        Object[] b = new Object[]{"x", "y", "z"};
        return Arrays.deepEquals(a, b) ? 1 : 0; // 1
    }

    public static int deepEqualsFlatUnequal() {
        Object[] a = new Object[]{"x", "y", "z"};
        Object[] b = new Object[]{"x", "y", "w"};
        return Arrays.deepEquals(a, b) ? 1 : 0; // 0
    }

    public static int deepHashFlat() {
        Object[] a = new Object[]{"a", "b", null, "c"};
        return Arrays.deepHashCode(a);
    }

    public static int hashLong() {
        return Arrays.hashCode(new long[]{1L, 2L, 3L});
    }

    public static int hashObject() {
        return Arrays.hashCode(new Object[]{"a", "b", null, "c"});
    }

    public static int streamSum() {
        return Arrays.stream(new int[]{1, 2, 3, 4}).sum(); // 10
    }

    public static int deepToStringNested() {
        Object[] a = new Object[]{new int[]{1, 2}, new int[]{3}};
        return checksum(Arrays.deepToString(a)); // "[[1, 2], [3]]"
    }

    public static int deepEqualsNestedEqual() {
        Object[] a = new Object[]{new int[]{1, 2}, new int[]{3}};
        Object[] b = new Object[]{new int[]{1, 2}, new int[]{3}};
        return Arrays.deepEquals(a, b) ? 1 : 0; // 1
    }

    public static int deepHashNested() {
        Object[] a = new Object[]{new int[]{1, 2}, new int[]{3, 4}};
        return Arrays.deepHashCode(a);
    }
}
