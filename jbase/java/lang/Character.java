package java.lang;

public final class Character {
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
}
