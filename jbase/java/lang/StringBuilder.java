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

    public StringBuilder insert(int offset, int i) {
        return insert(offset, String.valueOf(i));
    }

    public StringBuilder insert(int offset, long l) {
        return insert(offset, String.valueOf(l));
    }

    public StringBuilder insert(int offset, boolean b) {
        return insert(offset, b ? "true" : "false");
    }

    public StringBuilder insert(int offset, char c) {
        if (offset < 0 || offset > count) {
            throw new StringIndexOutOfBoundsException();
        }
        ensure(count + 1);
        for (int i = count - 1; i >= offset; i--) {
            value[i + 1] = value[i];
        }
        value[offset] = c;
        count++;
        return this;
    }

    public StringBuilder insert(int offset, char[] str) {
        if (offset < 0 || offset > count) {
            throw new StringIndexOutOfBoundsException();
        }
        int len = str.length;
        ensure(count + len);
        for (int i = count - 1; i >= offset; i--) {
            value[i + len] = value[i];
        }
        for (int i = 0; i < len; i++) {
            value[offset + i] = str[i];
        }
        count += len;
        return this;
    }

    public StringBuilder replace(int start, int end, String str) {
        if (start < 0 || start > count || start > end) {
            throw new StringIndexOutOfBoundsException();
        }
        if (end > count) {
            end = count;
        }
        String s = (str == null) ? "null" : str;
        int slen = s.length();
        int rlen = end - start;
        int diff = slen - rlen;
        if (diff > 0) {
            ensure(count + diff);
            for (int i = count - 1; i >= end; i--) {
                value[i + diff] = value[i];
            }
        } else if (diff < 0) {
            for (int i = end; i < count; i++) {
                value[i + diff] = value[i];
            }
        }
        for (int i = 0; i < slen; i++) {
            value[start + i] = s.charAt(i);
        }
        count += diff;
        return this;
    }

    public int indexOf(String str) {
        return indexOf(str, 0);
    }

    public int indexOf(String str, int fromIndex) {
        int slen = str.length();
        int from = fromIndex;
        if (from < 0) {
            from = 0;
        }
        if (slen == 0) {
            return from > count ? count : from;
        }
        char first = str.charAt(0);
        int max = count - slen;
        for (int i = from; i <= max; i++) {
            if (value[i] == first) {
                int j = 1;
                while (j < slen && value[i + j] == str.charAt(j)) {
                    j++;
                }
                if (j == slen) {
                    return i;
                }
            }
        }
        return -1;
    }

    public int lastIndexOf(String str) {
        return lastIndexOf(str, count);
    }

    public int lastIndexOf(String str, int fromIndex) {
        int slen = str.length();
        int start = fromIndex;
        int max = count - slen;
        if (start > max) {
            start = max;
        }
        if (slen == 0) {
            return start < 0 ? -1 : start;
        }
        char first = str.charAt(0);
        for (int i = start; i >= 0; i--) {
            if (value[i] == first) {
                int j = 1;
                while (j < slen && value[i + j] == str.charAt(j)) {
                    j++;
                }
                if (j == slen) {
                    return i;
                }
            }
        }
        return -1;
    }

    public StringBuilder appendCodePoint(int codePoint) {
        if (codePoint < 0 || codePoint > 0x10FFFF) {
            throw new IllegalArgumentException();
        }
        if (codePoint <= 0xFFFF) {
            return append((char) codePoint);
        }
        int cp = codePoint - 0x10000;
        char high = (char) (0xD800 + (cp >> 10));
        char low = (char) (0xDC00 + (cp & 0x3FF));
        ensure(count + 2);
        value[count++] = high;
        value[count++] = low;
        return this;
    }

    public int capacity() {
        return value.length;
    }

    public void ensureCapacity(int minimumCapacity) {
        if (minimumCapacity > 0) {
            ensure(minimumCapacity);
        }
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
