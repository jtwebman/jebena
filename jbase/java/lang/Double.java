package java.lang;

public final class Double extends Number implements Comparable<Double> {
    private final double value;

    public Double(double value) {
        this.value = value;
    }

    public static Double valueOf(double d) {
        return new Double(d);
    }

    public int intValue() {
        return (int) value;
    }

    public long longValue() {
        return (long) value;
    }

    public float floatValue() {
        return (float) value;
    }

    public double doubleValue() {
        return value;
    }

    public int hashCode() {
        long bits = doubleToLongBits(value);
        return (int) (bits ^ (bits >>> 32));
    }

    public boolean equals(Object o) {
        if (o instanceof Double) {
            return doubleToLongBits(value) == doubleToLongBits(((Double) o).value);
        }
        return false;
    }

    public int compareTo(Double other) {
        return compare(value, other.value);
    }

    public static int compare(double a, double b) {
        if (a < b) {
            return -1;
        }
        if (a > b) {
            return 1;
        }
        long abits = doubleToLongBits(a);
        long bbits = doubleToLongBits(b);
        return (abits == bbits) ? 0 : ((abits < bbits) ? -1 : 1);
    }

    public String toString() {
        return String.valueOf(value);
    }

    public static String toString(double d) {
        return String.valueOf(d);
    }

    public boolean isNaN() {
        return value != value;
    }

    public static native long doubleToLongBits(double value);

    public static native long doubleToRawLongBits(double value);

    public static native double longBitsToDouble(long bits);
}
