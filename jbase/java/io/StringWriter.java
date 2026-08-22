package java.io;

/**
 * Clean-room java.io.StringWriter. Collects written characters into a
 * StringBuilder that can be read back as a String or as the live buffer.
 * flush and close are no-ops; the buffer stays usable after close.
 */
public class StringWriter extends Writer {

    private final StringBuilder buf;

    public StringWriter() {
        this.buf = new StringBuilder();
    }

    public StringWriter(int initialSize) {
        this.buf = new StringBuilder(initialSize);
    }

    public void write(char[] cbuf, int off, int len) throws IOException {
        for (int i = 0; i < len; i++) {
            buf.append(cbuf[off + i]);
        }
    }

    public void flush() throws IOException {}

    public void close() throws IOException {}

    public StringBuilder getBuffer() {
        return buf;
    }

    public String toString() {
        return buf.toString();
    }
}
