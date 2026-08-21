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
}
