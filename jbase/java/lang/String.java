package java.lang;

/**
 * Clean-room java.lang.String for Jebena, backed by a char[] (UTF-16 code units).
 * String literals (ldc) are built directly by the VM with `value` set; new
 * String(char[]) copies. Methods are ordinary bytecode over `value` — the Zig
 * string intrinsic now only backs the bootstrap stub, not this real class.
 */
public final class String implements CharSequence, Comparable<String> {
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

    public String toString() {
        return this;
    }

    public CharSequence subSequence(int begin, int end) {
        return substring(begin, end);
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
    public boolean endsWith(String suffix) {
        int slen = suffix.value.length;
        if (slen > value.length) {
            return false;
        }
        int off = value.length - slen;
        for (int i = 0; i < slen; i++) {
            if (value[off + i] != suffix.value[i]) {
                return false;
            }
        }
        return true;
    }

    public int lastIndexOf(int ch) {
        for (int i = value.length - 1; i >= 0; i--) {
            if (value[i] == ch) {
                return i;
            }
        }
        return -1;
    }

    public int indexOf(String str) {
        int n = str.value.length;
        if (n == 0) {
            return 0;
        }
        int last = value.length - n;
        for (int i = 0; i <= last; i++) {
            int j = 0;
            while (j < n && value[i + j] == str.value[j]) {
                j++;
            }
            if (j == n) {
                return i;
            }
        }
        return -1;
    }

    public boolean equalsIgnoreCase(String s) {
        if (s == null || value.length != s.value.length) {
            return false;
        }
        for (int i = 0; i < value.length; i++) {
            char a = value[i];
            char b = s.value[i];
            if (a != b && lower(a) != lower(b)) {
                return false;
            }
        }
        return true;
    }

    private static char lower(char c) {
        return (c >= 'A' && c <= 'Z') ? (char) (c + 32) : c;
    }

    public char[] toCharArray() {
        char[] r = new char[value.length];
        for (int i = 0; i < value.length; i++) {
            r[i] = value[i];
        }
        return r;
    }

    public String trim() {
        int start = 0;
        int end = value.length;
        while (start < end && value[start] <= ' ') {
            start++;
        }
        while (end > start && value[end - 1] <= ' ') {
            end--;
        }
        return substring(start, end);
    }

    public String toUpperCase() {
        char[] r = new char[value.length];
        for (int i = 0; i < value.length; i++) {
            char c = value[i];
            r[i] = (c >= 'a' && c <= 'z') ? (char) (c - 32) : c;
        }
        return new String(r);
    }

    public String toLowerCase() {
        char[] r = new char[value.length];
        for (int i = 0; i < value.length; i++) {
            r[i] = lower(value[i]);
        }
        return new String(r);
    }

    public String replace(char oldChar, char newChar) {
        char[] r = new char[value.length];
        for (int i = 0; i < value.length; i++) {
            r[i] = (value[i] == oldChar) ? newChar : value[i];
        }
        return new String(r);
    }

    public static String valueOf(char c) {
        char[] r = { c };
        return new String(r);
    }

    public static String valueOf(boolean b) {
        return b ? "true" : "false";
    }

    public static String valueOf(char[] data) {
        return new String(data);
    }

    public static String valueOf(Object o) {
        return (o == null) ? "null" : o.toString();
    }

    public static String format(String fmt, Object... args) {
        return java.util.Formatter.formatStatic(fmt, args);
    }

    // Numeric valueOf reuse the VM's decimal/shortest-float formatting.
    public static native String valueOf(int i);

    public static native String valueOf(long l);

    public static native String valueOf(double d);

    public static native String valueOf(float f);
}
