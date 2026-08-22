package java.io;

/**
 * Clean-room java.io.InputStream. Abstract byte-source base: subclasses supply the
 * single-byte read() returning 0..255 or -1 at end of stream; the bulk overload
 * here loops over it. Signatures declare throws IOException to match the platform.
 */
public abstract class InputStream {

    public abstract int read() throws IOException;

    public int read(byte[] b, int off, int len) throws IOException {
        if (len <= 0) {
            return 0;
        }
        int first = read();
        if (first < 0) {
            return -1;
        }
        b[off] = (byte) first;
        int count = 1;
        while (count < len) {
            int c = read();
            if (c < 0) {
                break;
            }
            b[off + count] = (byte) c;
            count += 1;
        }
        return count;
    }

    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    public void close() throws IOException {}
}
