package java.lang;

public final class Byte extends Number implements Comparable<Byte> {
    private final byte value;

    public Byte(byte value) {
        this.value = value;
    }

    private static final Byte[] cache = new Byte[256];

    static {
        for (int i = 0; i < 256; i++) {
            cache[i] = new Byte((byte) (i - 128));
        }
    }

    public static Byte valueOf(byte b) {
        return cache[b + 128];
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
        if (o instanceof Byte) {
            return value == ((Byte) o).value;
        }
        return false;
    }

    public int compareTo(Byte other) {
        return value - other.value;
    }

    public String toString() {
        return String.valueOf((int) value);
    }
}
