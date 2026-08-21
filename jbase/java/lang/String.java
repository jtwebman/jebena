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

    public String concat(String s) {
        int len = value.length;
        int slen = s.value.length;
        char[] r = new char[len + slen];
        for (int i = 0; i < len; i++) {
            r[i] = value[i];
        }
        for (int i = 0; i < slen; i++) {
            r[len + i] = s.value[i];
        }
        return new String(r);
    }

    public String substring(int begin) {
        return substring(begin, value.length);
    }

    public String substring(int begin, int end) {
        if (begin < 0 || end > value.length || begin > end) {
            throw new StringIndexOutOfBoundsException();
        }
        int n = end - begin;
        char[] r = new char[n];
        for (int i = 0; i < n; i++) {
            r[i] = value[begin + i];
        }
        return new String(r);
    }

    public int indexOf(int ch) {
        for (int i = 0; i < value.length; i++) {
            if (value[i] == ch) {
                return i;
            }
        }
        return -1;
    }

    public boolean startsWith(String prefix) {
        if (prefix.value.length > value.length) {
            return false;
        }
        for (int i = 0; i < prefix.value.length; i++) {
            if (value[i] != prefix.value[i]) {
                return false;
            }
        }
        return true;
    }

    public int compareTo(String s) {
        int n = Math.min(value.length, s.value.length);
        for (int i = 0; i < n; i++) {
            if (value[i] != s.value[i]) {
                return value[i] - s.value[i];
            }
        }
        return value.length - s.value.length;
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
