package java.io;

/**
 * Clean-room java.io.StringReader. Reads characters out of a String. Tracks a
 * cursor; returns -1 once the whole string has been consumed.
 */
public class StringReader extends Reader {

    private final String str;
    private final int length;
    private int next;

    public StringReader(String s) {
        this.str = s;
        this.length = s.length();
        this.next = 0;
    }

    public int read(char[] cbuf, int off, int len) throws IOException {
        if (next >= length) {
            return -1;
        }
        if (len <= 0) {
            return 0;
        }
        int n = length - next;
        if (n > len) {
            n = len;
        }
        for (int i = 0; i < n; i++) {
            cbuf[off + i] = str.charAt(next + i);
        }
        next += n;
        return n;
    }

    public int read() throws IOException {
        if (next >= length) {
            return -1;
        }
        char c = str.charAt(next);
        next += 1;
        return c;
    }

    public void close() throws IOException {}
}
