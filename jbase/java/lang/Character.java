package java.lang;

public final class Character implements Comparable<Character> {
    public static final Class TYPE = Class.getPrimitiveClass("char");

    private final char value;

    public Character(char value) {
        this.value = value;
    }

    private static final Character[] cache = new Character[128];

    static {
        for (int i = 0; i < 128; i++) {
            cache[i] = new Character((char) i);
        }
    }

    public static Character valueOf(char c) {
        if (c <= 127) {
            return cache[c];
        }
        return new Character(c);
    }

    public char charValue() {
        return value;
    }

    public int hashCode() {
        return value;
    }

    public boolean equals(Object o) {
        if (o instanceof Character) {
            return value == ((Character) o).value;
        }
        return false;
    }

    public int compareTo(Character other) {
        return value - other.value;
    }

    public String toString() {
        char[] r = { value };
        return new String(r);
    }

    public static String toString(char c) {
        char[] r = { c };
        return new String(r);
    }

    // Classification for the ASCII range (matches java.lang.Character for ASCII;
    // full Unicode tables are out of scope). Mirrors the VM's Character intrinsic.
    private static boolean asciiLetter(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    public static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    public static boolean isLetter(char c) {
        return asciiLetter(c);
    }

    public static boolean isLetterOrDigit(char c) {
        return asciiLetter(c) || (c >= '0' && c <= '9');
    }

    public static boolean isUpperCase(char c) {
        return c >= 'A' && c <= 'Z';
    }

    public static boolean isLowerCase(char c) {
        return c >= 'a' && c <= 'z';
    }

    public static boolean isWhitespace(char c) {
        return c == ' ' || (c >= '\t' && c <= '\r') || (c >= 0x1C && c <= 0x1F);
    }

    public static boolean isSpaceChar(char c) {
        return c == ' ';
    }

    public static char toUpperCase(char c) {
        return (c >= 'a' && c <= 'z') ? (char) (c - 32) : c;
    }

    public static char toLowerCase(char c) {
        return (c >= 'A' && c <= 'Z') ? (char) (c + 32) : c;
    }

    public static int compare(char a, char b) {
        return a - b;
    }

    public static int digit(char c, int radix) {
        int d = -1;
        if (c >= '0' && c <= '9') {
            d = c - '0';
        } else if (c >= 'a' && c <= 'z') {
            d = c - 'a' + 10;
        } else if (c >= 'A' && c <= 'Z') {
            d = c - 'A' + 10;
        }
        return (d < radix) ? d : -1;
    }

    public static int getNumericValue(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (asciiLetter(c)) {
            return toLowerCase(c) - 'a' + 10;
        }
        return -1;
    }

    public static final char MIN_HIGH_SURROGATE = '\uD800';
    public static final char MAX_HIGH_SURROGATE = '\uDBFF';
    public static final char MIN_LOW_SURROGATE = '\uDC00';
    public static final char MAX_LOW_SURROGATE = '\uDFFF';
    public static final char MIN_SURROGATE = MIN_HIGH_SURROGATE;
    public static final char MAX_SURROGATE = MAX_LOW_SURROGATE;
    public static final int MIN_SUPPLEMENTARY_CODE_POINT = 0x010000;
    public static final int MIN_CODE_POINT = 0x000000;
    public static final int MAX_CODE_POINT = 0x10FFFF;

    public static int charCount(int codePoint) {
        return codePoint >= MIN_SUPPLEMENTARY_CODE_POINT ? 2 : 1;
    }

    public static boolean isHighSurrogate(char c) {
        return c >= MIN_HIGH_SURROGATE && c <= MAX_HIGH_SURROGATE;
    }

    public static boolean isLowSurrogate(char c) {
        return c >= MIN_LOW_SURROGATE && c <= MAX_LOW_SURROGATE;
    }

    public static boolean isSurrogate(char c) {
        return c >= MIN_SURROGATE && c <= MAX_SURROGATE;
    }

    public static int toCodePoint(char high, char low) {
        return ((high - MIN_HIGH_SURROGATE) << 10)
            + (low - MIN_LOW_SURROGATE)
            + MIN_SUPPLEMENTARY_CODE_POINT;
    }

    public static char[] toChars(int codePoint) {
        if (codePoint < 0 || codePoint > MAX_CODE_POINT) {
            throw new IllegalArgumentException(
                "Not a valid Unicode code point: 0x" + Integer.toHexString(codePoint));
        }
        if (codePoint < MIN_SUPPLEMENTARY_CODE_POINT) {
            char[] r = { (char) codePoint };
            return r;
        }
        int offset = codePoint - MIN_SUPPLEMENTARY_CODE_POINT;
        char high = (char) (MIN_HIGH_SURROGATE + (offset >> 10));
        char low = (char) (MIN_LOW_SURROGATE + (offset & 0x3FF));
        char[] r = { high, low };
        return r;
    }

    public static int codePointAt(CharSequence seq, int index) {
        char c1 = seq.charAt(index);
        if (isHighSurrogate(c1) && (index + 1) < seq.length()) {
            char c2 = seq.charAt(index + 1);
            if (isLowSurrogate(c2)) {
                return toCodePoint(c1, c2);
            }
        }
        return c1;
    }

    public static boolean isAlphabetic(int codePoint) {
        if (codePoint >= 'A' && codePoint <= 'Z') {
            return true;
        }
        if (codePoint >= 'a' && codePoint <= 'z') {
            return true;
        }
        return false;
    }
}
