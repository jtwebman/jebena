package java.lang;

/**
 * Clean-room java.lang.String for Jebena, backed by a char[] (UTF-16 code units).
 * String literals (ldc) are built directly by the VM with `value` set; new
 * String(char[]) copies. Methods are ordinary bytecode over `value` — the Zig
 * string intrinsic now only backs the bootstrap stub, not this real class.
 */
public final class String {
    private final char[] value;

    public String(char[] v) {
        char[] c = new char[v.length];
        for (int i = 0; i < v.length; i++) {
            c[i] = v[i];
        }
        this.value = c;
    }

    public int length() {
        return value.length;
    }

    public boolean isEmpty() {
        return value.length == 0;
    }

    public char charAt(int index) {
        if (index < 0 || index >= value.length) {
            throw new StringIndexOutOfBoundsException();
        }
        return value[index];
    }

    public int hashCode() {
        int h = 0;
        for (int i = 0; i < value.length; i++) {
            h = 31 * h + value[i];
        }
        return h;
    }

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof String)) {
            return false;
        }
        String s = (String) other;
        if (value.length != s.value.length) {
            return false;
        }
        for (int i = 0; i < value.length; i++) {
            if (value[i] != s.value[i]) {
                return false;
            }
        }
        return true;
    }
}
