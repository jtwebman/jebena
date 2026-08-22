package java.io;

/**
 * Clean-room java.io.CharArrayWriter. An in-memory character buffer, backed here
 * by a StringBuilder. Characters accumulate and can be read back as a String or
 * measured with size(). flush and close are no-ops.
 */
public class CharArrayWriter extends Writer {

    private final StringBuilder buf;

    public CharArrayWriter() {
        this.buf = new StringBuilder();
    }

    public CharArrayWriter(int initialSize) {
        this.buf = new StringBuilder(initialSize);
    }

    public void write(char[] cbuf, int off, int len) throws IOException {
        for (int i = 0; i < len; i++) {
            buf.append(cbuf[off + i]);
        }
    }

    public void flush() throws IOException {}

    public void close() throws IOException {}

    public int size() {
        return buf.length();
    }

    public void reset() {
        buf.setLength(0);
    }

    public char[] toCharArray() {
        return buf.toString().toCharArray();
    }

    public String toString() {
        return buf.toString();
    }
}
