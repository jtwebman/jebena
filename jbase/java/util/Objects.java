package java.util;

public final class Objects {
    private Objects() {}

    public static boolean equals(Object a, Object b) {
        return (a == b) || (a != null && a.equals(b));
    }

    public static int hashCode(Object o) {
        return o != null ? o.hashCode() : 0;
    }

    public static String toString(Object o) {
        return String.valueOf(o);
    }

    public static boolean isNull(Object o) {
        return o == null;
    }

    public static boolean nonNull(Object o) {
        return o != null;
    }

    public static Object requireNonNull(Object obj) {
        if (obj == null) {
            throw new NullPointerException();
        }
        return obj;
    }

    public static Object requireNonNull(Object obj, String message) {
        if (obj == null) {
            throw new NullPointerException(message);
        }
        return obj;
    }

    public static Object requireNonNull(Object obj, java.util.function.Supplier messageSupplier) {
        if (obj == null) {
            throw new NullPointerException(messageSupplier == null ? null : (String) messageSupplier.get());
        }
        return obj;
    }

    public static Object requireNonNullElse(Object obj, Object defaultObj) {
        return (obj != null) ? obj : requireNonNull(defaultObj, "defaultObj");
    }

    public static Object requireNonNullElseGet(Object obj, java.util.function.Supplier supplier) {
        if (obj != null) {
            return obj;
        }
        requireNonNull(supplier, "supplier");
        return requireNonNull(supplier.get(), "supplier.get()");
    }

    public static int hash(Object... values) {
        return java.util.Arrays.hashCode(values);
    }

    public static String toString(Object o, String nullDefault) {
        return (o != null) ? o.toString() : nullDefault;
    }

    public static int checkIndex(int index, int length) {
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException(
                "Index " + index + " out of bounds for length " + length);
        }
        return index;
    }

    public static int checkFromToIndex(int fromIndex, int toIndex, int length) {
        if (fromIndex < 0 || fromIndex > toIndex || toIndex > length) {
            throw new IndexOutOfBoundsException(
                "Range [" + fromIndex + ", " + toIndex + ") out of bounds for length " + length);
        }
        return fromIndex;
    }

    public static int checkFromIndexSize(int fromIndex, int size, int length) {
        if (length < 0 || size < 0 || fromIndex < 0 || fromIndex > length - size) {
            throw new IndexOutOfBoundsException(
                "Range [" + fromIndex + ", " + fromIndex + " + " + size + ") out of bounds for length " + length);
        }
        return fromIndex;
    }
}
