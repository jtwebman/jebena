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
}
