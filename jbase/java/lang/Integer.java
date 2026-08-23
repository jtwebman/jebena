package java.lang;

/**
 * Clean-room java.lang.Integer for Jebena. A real boxed instance holds an int
 * value; valueOf caches -128..127 so autoboxed small ints are ==, matching the
 * spec. The cache is an ordinary static field (a GC root), built in <clinit>.
 */
public final class Integer extends Number implements Comparable<Integer> {
    public static final Class TYPE = Class.getPrimitiveClass("int");
    public static final int MIN_VALUE = 0x80000000;
    public static final int MAX_VALUE = 0x7fffffff;
    public static final int SIZE = 32;
    public static final int BYTES = 4;

    private final int value;

    public Integer(int value) {
        this.value = value;
    }

    private static final Integer[] cache = new Integer[256];

    static {
        for (int i = 0; i < 256; i++) {
            cache[i] = new Integer(i - 128);
        }
    }

    public static Integer valueOf(int i) {
        if (i >= -128 && i <= 127) {
            return cache[i + 128];
        }
        return new Integer(i);
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
        if (o instanceof Integer) {
            return value == ((Integer) o).value;
        }
        return false;
    }

    public int compareTo(Integer other) {
        return (value < other.value) ? -1 : ((value == other.value) ? 0 : 1);
    }

    public String toString() {
        return String.valueOf(value);
    }

    public static String toString(int i) {
        return String.valueOf(i);
    }

    public static int parseInt(String s) {
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
        int result = 0;
        for (; i < len; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                throw new NumberFormatException(s);
            }
            result = result * 10 + (c - '0');
        }
        return neg ? -result : result;
    }

    public static Integer valueOf(String s) {
        return valueOf(parseInt(s));
    }

    private static String toUnsignedString(int i, int shift) {
        if (i == 0) {
            return "0";
        }
        int mask = (1 << shift) - 1;
        char[] buf = new char[32];
        int p = 32;
        int v = i;
        while (v != 0) {
            int d = v & mask;
            buf[--p] = (char) (d < 10 ? ('0' + d) : ('a' + d - 10));
            v >>>= shift;
        }
        int len = 32 - p;
        char[] r = new char[len];
        for (int k = 0; k < len; k++) {
            r[k] = buf[p + k];
        }
        return new String(r);
    }

    public static String toHexString(int i) {
        return toUnsignedString(i, 4);
    }

    public static String toOctalString(int i) {
        return toUnsignedString(i, 3);
    }

    public static String toBinaryString(int i) {
        return toUnsignedString(i, 1);
    }

    public static int bitCount(int i) {
        i = i - ((i >>> 1) & 0x55555555);
        i = (i & 0x33333333) + ((i >>> 2) & 0x33333333);
        i = (i + (i >>> 4)) & 0x0f0f0f0f;
        i = i + (i >>> 8);
        i = i + (i >>> 16);
        return i & 0x3f;
    }

    public static int numberOfLeadingZeros(int i) {
        if (i <= 0) {
            return i == 0 ? 32 : 0;
        }
        int n = 31;
        if (i >= 1 << 16) {
            n -= 16;
            i >>>= 16;
        }
        if (i >= 1 << 8) {
            n -= 8;
            i >>>= 8;
        }
        if (i >= 1 << 4) {
            n -= 4;
            i >>>= 4;
        }
        if (i >= 1 << 2) {
            n -= 2;
            i >>>= 2;
        }
        return n - (i >>> 1);
    }

    public static int numberOfTrailingZeros(int i) {
        i = ~i & (i - 1);
        if (i <= 0) {
            return i & 32;
        }
        int n = 1;
        if (i > 1 << 16) {
            n += 16;
            i >>>= 16;
        }
        if (i > 1 << 8) {
            n += 8;
            i >>>= 8;
        }
        if (i > 1 << 4) {
            n += 4;
            i >>>= 4;
        }
        if (i > 1 << 2) {
            n += 2;
            i >>>= 2;
        }
        return n + (i >>> 1);
    }

    public static int highestOneBit(int i) {
        return i & (0x80000000 >>> numberOfLeadingZeros(i));
    }

    public static int lowestOneBit(int i) {
        return i & -i;
    }

    public static int reverse(int i) {
        i = (i & 0x55555555) << 1 | (i >>> 1) & 0x55555555;
        i = (i & 0x33333333) << 2 | (i >>> 2) & 0x33333333;
        i = (i & 0x0f0f0f0f) << 4 | (i >>> 4) & 0x0f0f0f0f;
        i = (i << 24) | ((i & 0xff00) << 8) | ((i >>> 8) & 0xff00) | (i >>> 24);
        return i;
    }
}
