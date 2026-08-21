package java.util;

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
}
