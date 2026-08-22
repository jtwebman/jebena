package java.lang;

public final class Short extends Number implements Comparable<Short> {
    public static final Class TYPE = Class.getPrimitiveClass("short");

    private final short value;

    public Short(short value) {
        this.value = value;
    }

    private static final Short[] cache = new Short[256];

    static {
        for (int i = 0; i < 256; i++) {
            cache[i] = new Short((short) (i - 128));
        }
    }

    public static Short valueOf(short s) {
        if (s >= -128 && s <= 127) {
            return cache[s + 128];
        }
        return new Short(s);
    }

    public int intValue() {
        return value;
    }

    public long longValue() {
        return (long) value;
    }

    public float floatValue() {
        return (float) value;
    }

    public double doubleValue() {
        return (double) value;
    }

    public int hashCode() {
        return value;
    }

    public boolean equals(Object o) {
        if (o instanceof Short) {
            return value == ((Short) o).value;
        }
        return false;
    }

    public int compareTo(Short other) {
        return value - other.value;
    }

    public String toString() {
        return String.valueOf((int) value);
    }
}
