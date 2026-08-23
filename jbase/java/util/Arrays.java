package java.util;

import java.util.stream.IntStream;
import java.util.function.IntUnaryOperator;

/**
 * Clean-room java.util.Arrays (a subset). Insertion sorts (stable, same result as
 * the spec's sorts for a total order). asList returns a fresh ArrayList copy.
 */
public final class Arrays {
    private Arrays() {}

    public static List asList(Object[] a) {
        ArrayList list = new ArrayList(a.length < 1 ? 1 : a.length);
        for (int i = 0; i < a.length; i++) {
            list.add(a[i]);
        }
        return list;
    }

    public static String toString(int[] a) {
        if (a == null) {
            return "null";
        }
        String s = "[";
        for (int i = 0; i < a.length; i++) {
            if (i > 0) {
                s = s + ", ";
            }
            s = s + a[i];
        }
        return s + "]";
    }

    public static String toString(long[] a) {
        if (a == null) {
            return "null";
        }
        String s = "[";
        for (int i = 0; i < a.length; i++) {
            if (i > 0) {
                s = s + ", ";
            }
            s = s + a[i];
        }
        return s + "]";
    }

    public static String toString(Object[] a) {
        if (a == null) {
            return "null";
        }
        String s = "[";
        for (int i = 0; i < a.length; i++) {
            if (i > 0) {
                s = s + ", ";
            }
            s = s + String.valueOf(a[i]);
        }
        return s + "]";
    }

    public static int[] copyOf(int[] a, int newLength) {
        int[] r = new int[newLength];
        int n = Math.min(a.length, newLength);
        for (int i = 0; i < n; i++) {
            r[i] = a[i];
        }
        return r;
    }

    public static Object[] copyOf(Object[] a, int newLength) {
        Object[] r = new Object[newLength];
        int n = Math.min(a.length, newLength);
        for (int i = 0; i < n; i++) {
            r[i] = a[i];
        }
        return r;
    }

    public static int[] copyOfRange(int[] a, int from, int to) {
        int len = to - from;
        int[] r = new int[len];
        for (int i = 0; i < len && from + i < a.length; i++) {
            r[i] = a[from + i];
        }
        return r;
    }

    public static void sort(int[] a) {
        for (int i = 1; i < a.length; i++) {
            int key = a[i];
            int j = i - 1;
            while (j >= 0 && a[j] > key) {
                a[j + 1] = a[j];
                j--;
            }
            a[j + 1] = key;
        }
    }

    public static void sort(Object[] a) {
        for (int i = 1; i < a.length; i++) {
            Object key = a[i];
            int j = i - 1;
            while (j >= 0 && ((Comparable) a[j]).compareTo(key) > 0) {
                a[j + 1] = a[j];
                j--;
            }
            a[j + 1] = key;
        }
    }

    public static boolean equals(int[] a, int[] b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null || a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    public static void fill(int[] a, int val) {
        for (int i = 0; i < a.length; i++) {
            a[i] = val;
        }
    }

    public static void fill(int[] a, int fromIndex, int toIndex, int val) {
        rangeCheck(a.length, fromIndex, toIndex);
        for (int i = fromIndex; i < toIndex; i++) {
            a[i] = val;
        }
    }

    public static void fill(long[] a, long val) {
        for (int i = 0; i < a.length; i++) {
            a[i] = val;
        }
    }

    public static void fill(long[] a, int fromIndex, int toIndex, long val) {
        rangeCheck(a.length, fromIndex, toIndex);
        for (int i = fromIndex; i < toIndex; i++) {
            a[i] = val;
        }
    }

    public static void fill(char[] a, char val) {
        for (int i = 0; i < a.length; i++) {
            a[i] = val;
        }
    }

    public static void fill(char[] a, int fromIndex, int toIndex, char val) {
        rangeCheck(a.length, fromIndex, toIndex);
        for (int i = fromIndex; i < toIndex; i++) {
            a[i] = val;
        }
    }

    public static void setAll(int[] a, IntUnaryOperator generator) {
        for (int i = 0; i < a.length; i++) {
            a[i] = generator.applyAsInt(i);
        }
    }

    public static boolean equals(int[] a, int aFromIndex, int aToIndex,
                                 int[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);
        int aLength = aToIndex - aFromIndex;
        int bLength = bToIndex - bFromIndex;
        if (aLength != bLength) {
            return false;
        }
        for (int i = 0; i < aLength; i++) {
            if (a[aFromIndex + i] != b[bFromIndex + i]) {
                return false;
            }
        }
        return true;
    }

    public static int mismatch(int[] a, int[] b) {
        int length = Math.min(a.length, b.length);
        for (int i = 0; i < length; i++) {
            if (a[i] != b[i]) {
                return i;
            }
        }
        if (a.length != b.length) {
            return length;
        }
        return -1;
    }

    private static void rangeCheck(int arrayLength, int fromIndex, int toIndex) {
        if (fromIndex > toIndex) {
            throw new IllegalArgumentException(
                "fromIndex(" + fromIndex + ") > toIndex(" + toIndex + ")");
        }
        if (fromIndex < 0) {
            throw new ArrayIndexOutOfBoundsException(String.valueOf(fromIndex));
        }
        if (toIndex > arrayLength) {
            throw new ArrayIndexOutOfBoundsException(String.valueOf(toIndex));
        }
    }

    public static int binarySearch(int[] a, int key) {
        int lo = 0;
        int hi = a.length - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int v = a[mid];
            if (v < key) {
                lo = mid + 1;
            } else if (v > key) {
                hi = mid - 1;
            } else {
                return mid;
            }
        }
        return -(lo + 1);
    }

    public static int hashCode(int[] a) {
        if (a == null) {
            return 0;
        }
        int result = 1;
        for (int i = 0; i < a.length; i++) {
            result = 31 * result + a[i];
        }
        return result;
    }

    public static int hashCode(long[] a) {
        if (a == null) {
            return 0;
        }
        int result = 1;
        for (int i = 0; i < a.length; i++) {
            long e = a[i];
            int elementHash = (int) (e ^ (e >>> 32));
            result = 31 * result + elementHash;
        }
        return result;
    }

    public static int hashCode(Object[] a) {
        if (a == null) {
            return 0;
        }
        int result = 1;
        for (int i = 0; i < a.length; i++) {
            Object e = a[i];
            result = 31 * result + (e == null ? 0 : e.hashCode());
        }
        return result;
    }

    public static int deepHashCode(Object[] a) {
        if (a == null) {
            return 0;
        }
        int result = 1;
        for (int i = 0; i < a.length; i++) {
            Object e = a[i];
            int elementHash;
            if (e == null) {
                elementHash = 0;
            } else if (e instanceof Object[]) {
                elementHash = deepHashCode((Object[]) e);
            } else if (e instanceof int[]) {
                elementHash = hashCode((int[]) e);
            } else if (e instanceof long[]) {
                elementHash = hashCode((long[]) e);
            } else {
                elementHash = e.hashCode();
            }
            result = 31 * result + elementHash;
        }
        return result;
    }

    public static String deepToString(Object[] a) {
        if (a == null) {
            return "null";
        }
        String s = "[";
        for (int i = 0; i < a.length; i++) {
            if (i > 0) {
                s = s + ", ";
            }
            Object e = a[i];
            if (e == null) {
                s = s + "null";
            } else if (e instanceof Object[]) {
                s = s + deepToString((Object[]) e);
            } else if (e instanceof int[]) {
                s = s + toString((int[]) e);
            } else if (e instanceof long[]) {
                s = s + toString((long[]) e);
            } else {
                s = s + String.valueOf(e);
            }
        }
        return s + "]";
    }

    public static boolean deepEquals(Object[] a, Object[] b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null || a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            Object e1 = a[i];
            Object e2 = b[i];
            if (!deepEqualsElement(e1, e2)) {
                return false;
            }
        }
        return true;
    }

    private static boolean deepEqualsElement(Object e1, Object e2) {
        if (e1 == e2) {
            return true;
        }
        if (e1 == null || e2 == null) {
            return false;
        }
        if (e1 instanceof Object[] && e2 instanceof Object[]) {
            return deepEquals((Object[]) e1, (Object[]) e2);
        }
        if (e1 instanceof int[] && e2 instanceof int[]) {
            return equals((int[]) e1, (int[]) e2);
        }
        if (e1 instanceof long[] && e2 instanceof long[]) {
            long[] l1 = (long[]) e1;
            long[] l2 = (long[]) e2;
            if (l1.length != l2.length) {
                return false;
            }
            for (int i = 0; i < l1.length; i++) {
                if (l1[i] != l2[i]) {
                    return false;
                }
            }
            return true;
        }
        return e1.equals(e2);
    }

    public static IntStream stream(int[] a) {
        return IntStream.of(a);
    }
}
