package java.lang;

public final class Long extends Number implements Comparable<Long> {
    public static final Class TYPE = Class.getPrimitiveClass("long");
    public static final long MIN_VALUE = 0x8000000000000000L;
    public static final long MAX_VALUE = 0x7fffffffffffffffL;
    public static final int SIZE = 64;
    public static final int BYTES = 8;

    private final long value;

    public Long(long value) {
        this.value = value;
    }

    private static final Long[] cache = new Long[256];

    static {
        for (int i = 0; i < 256; i++) {
            cache[i] = new Long(i - 128);
        }
    }

    public static Long valueOf(long l) {
        if (l >= -128 && l <= 127) {
            return cache[(int) (l + 128)];
        }
        return new Long(l);
    }

    public int intValue() {
        return (int) value;
    }

    public long longValue() {
        return value;
    }

    public float floatValue() {
        return (float) value;
    }

    public double doubleValue() {
        return (double) value;
    }

    public int hashCode() {
        return (int) (value ^ (value >>> 32));
    }

    public boolean equals(Object o) {
        if (o instanceof Long) {
            return value == ((Long) o).value;
        }
        return false;
    }

    public int compareTo(Long other) {
        return (value < other.value) ? -1 : ((value == other.value) ? 0 : 1);
    }

    public String toString() {
        return String.valueOf(value);
    }

    public static String toString(long l) {
        return String.valueOf(l);
    }

    public static long parseLong(String s) {
        int len = s.length();
        if (len == 0) {
            throw new NumberFormatException(s);
        }
        boolean neg = false;
        int i = 0;
        char first = s.charAt(0);
        if (first == '-') {
            neg = true;
            i = 1;
        } else if (first == '+') {
            i = 1;
        }
        if (i == len) {
            throw new NumberFormatException(s);
        }
        long result = 0;
        for (; i < len; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                throw new NumberFormatException(s);
            }
            result = result * 10 + (c - '0');
        }
        return neg ? -result : result;
    }

    public static Long valueOf(String s) {
        return valueOf(parseLong(s));
    }

    private static String toUnsignedString(long i, int shift) {
        if (i == 0) {
            return "0";
        }
        int mask = (1 << shift) - 1;
        int n = 0;
        long v = i;
        while (v != 0) {
            n++;
            v >>>= shift;
        }
        char[] buf = new char[n];
        v = i;
        for (int k = n - 1; k >= 0; k--) {
            int d = (int) (v & mask);
            buf[k] = (char) (d < 10 ? '0' + d : 'a' + (d - 10));
            v >>>= shift;
        }
        return new String(buf);
    }

    public static String toHexString(long i) {
        return toUnsignedString(i, 4);
    }

    public static String toOctalString(long i) {
        return toUnsignedString(i, 3);
    }

    public static String toBinaryString(long i) {
        return toUnsignedString(i, 1);
    }

    public static int signum(long i) {
        return (int) ((i >> 63) | (-i >>> 63));
    }

    public static int bitCount(long i) {
        i = i - ((i >>> 1) & 0x5555555555555555L);
        i = (i & 0x3333333333333333L) + ((i >>> 2) & 0x3333333333333333L);
        i = (i + (i >>> 4)) & 0x0f0f0f0f0f0f0f0fL;
        i = i + (i >>> 8);
        i = i + (i >>> 16);
        i = i + (i >>> 32);
        return (int) i & 0x7f;
    }
}
