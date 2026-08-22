package java.io;

/**
 * Clean-room java.io.ByteArrayInputStream. Reads bytes out of a supplied array.
 * read() returns the next byte as an int 0..255, or -1 once the array is
 * exhausted; the bulk read fills a caller array from the current position.
 */
public class ByteArrayInputStream extends InputStream {

    private final byte[] buf;
    private final int end;
    private int pos;

    public ByteArrayInputStream(byte[] buf) {
        this.buf = buf;
        this.pos = 0;
        this.end = buf.length;
    }

    public ByteArrayInputStream(byte[] buf, int offset, int length) {
        this.buf = buf;
        this.pos = offset;
        int limit = offset + length;
        this.end = limit > buf.length ? buf.length : limit;
    }

    public int read() throws IOException {
        if (pos >= end) {
            return -1;
        }
        int b = buf[pos] & 0xFF;
        pos += 1;
        return b;
    }

    public int read(byte[] b, int off, int len) throws IOException {
        if (pos >= end) {
            return -1;
        }
        if (len <= 0) {
            return 0;
        }
        int n = end - pos;
        if (n > len) {
            n = len;
        }
        for (int i = 0; i < n; i++) {
            b[off + i] = buf[pos + i];
        }
        pos += n;
        return n;
    }

    public int available() throws IOException {
        return end - pos;
    }
}
