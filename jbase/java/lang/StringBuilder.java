package java.lang;

/**
 * Clean-room java.lang.StringBuilder backed by a growable char[]. append(...)
 * returns this for chaining; toString() snapshots into a new String.
 */
public final class StringBuilder implements CharSequence {
    private char[] value;
    private int count;

    public StringBuilder() {
        value = new char[16];
        count = 0;
    }

    public StringBuilder(int capacity) {
        value = new char[capacity < 1 ? 1 : capacity];
        count = 0;
    }

    public StringBuilder(String str) {
        this();
        append(str);
    }

    private void ensure(int min) {
        if (min > value.length) {
            int nc = value.length * 2 + 2;
            if (nc < min) {
                nc = min;
            }
            char[] nv = new char[nc];
            for (int i = 0; i < count; i++) {
                nv[i] = value[i];
            }
            value = nv;
        }
    }

    public int length() {
        return count;
    }

    public char charAt(int index) {
        if (index < 0 || index >= count) {
            throw new StringIndexOutOfBoundsException();
        }
        return value[index];
    }

    public StringBuilder append(char c) {
        ensure(count + 1);
        value[count++] = c;
        return this;
    }

    public StringBuilder append(String str) {
        String s = (str == null) ? "null" : str;
        int len = s.length();
        ensure(count + len);
        for (int i = 0; i < len; i++) {
            value[count++] = s.charAt(i);
        }
        return this;
    }

    public StringBuilder append(CharSequence cs) {
        return append(cs == null ? "null" : cs.toString());
    }

    public StringBuilder append(Object o) {
        return append(String.valueOf(o));
    }

    public StringBuilder append(int i) {
        return append(String.valueOf(i));
    }

    public StringBuilder append(long l) {
        return append(String.valueOf(l));
    }

    public StringBuilder append(boolean b) {
        return append(b ? "true" : "false");
    }

    public StringBuilder append(double d) {
        return append(String.valueOf(d));
    }

    public StringBuilder append(float f) {
        return append(String.valueOf(f));
    }

    public StringBuilder append(char[] chars) {
        ensure(count + chars.length);
        for (int i = 0; i < chars.length; i++) {
            value[count++] = chars[i];
        }
        return this;
    }

    public StringBuilder reverse() {
        for (int i = 0, j = count - 1; i < j; i++, j--) {
            char t = value[i];
            value[i] = value[j];
            value[j] = t;
        }
        return this;
    }

    public StringBuilder deleteCharAt(int index) {
        if (index < 0 || index >= count) {
            throw new StringIndexOutOfBoundsException();
        }
        for (int i = index; i < count - 1; i++) {
            value[i] = value[i + 1];
        }
        count--;
        return this;
    }

    public StringBuilder delete(int start, int end) {
        if (end > count) {
            end = count;
        }
        if (start < 0 || start > count || start > end) {
            throw new StringIndexOutOfBoundsException();
        }
        int len = end - start;
        if (len > 0) {
            for (int i = end; i < count; i++) {
                value[i - len] = value[i];
            }
            count -= len;
        }
        return this;
    }

    public void setCharAt(int index, char ch) {
        if (index < 0 || index >= count) {
            throw new StringIndexOutOfBoundsException();
        }
        value[index] = ch;
    }

    public StringBuilder insert(int offset, String str) {
        if (offset < 0 || offset > count) {
            throw new StringIndexOutOfBoundsException();
        }
        String s = (str == null) ? "null" : str;
        int len = s.length();
        ensure(count + len);
        for (int i = count - 1; i >= offset; i--) {
            value[i + len] = value[i];
        }
        for (int i = 0; i < len; i++) {
            value[offset + i] = s.charAt(i);
        }
        count += len;
        return this;
    }

    public void setLength(int newLength) {
        if (newLength < 0) {
            throw new StringIndexOutOfBoundsException();
        }
        ensure(newLength);
        for (int i = count; i < newLength; i++) {
            value[i] = '\0';
        }
        count = newLength;
    }

    public CharSequence subSequence(int start, int end) {
        return toString().substring(start, end);
    }

    public String toString() {
        char[] r = new char[count];
        for (int i = 0; i < count; i++) {
            r[i] = value[i];
        }
        return new String(r);
    }
}
