package java.lang;

public final class Float extends Number implements Comparable<Float> {
    private final float value;

    public Float(float value) {
        this.value = value;
    }

    public static Float valueOf(float f) {
        return new Float(f);
    }

    public int intValue() {
        return (int) value;
    }

    public long longValue() {
        return (long) value;
    }

    public float floatValue() {
        return value;
    }

    public double doubleValue() {
        return (double) value;
    }

    public int hashCode() {
        return floatToIntBits(value);
    }

    public boolean equals(Object o) {
        if (o instanceof Float) {
            return floatToIntBits(value) == floatToIntBits(((Float) o).value);
        }
        return false;
    }

    public int compareTo(Float other) {
        return compare(value, other.value);
    }

    public static int compare(float a, float b) {
        if (a < b) {
            return -1;
        }
        if (a > b) {
            return 1;
        }
        int abits = floatToIntBits(a);
        int bbits = floatToIntBits(b);
        return (abits == bbits) ? 0 : ((abits < bbits) ? -1 : 1);
    }

    public String toString() {
        return String.valueOf(value);
    }

    public static String toString(float f) {
        return String.valueOf(f);
    }

    public static native int floatToIntBits(float value);

    public static native int floatToRawIntBits(float value);

    public static native float intBitsToFloat(int bits);
}
