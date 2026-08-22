package java.io;

/**
 * Clean-room java.io.ByteArrayOutputStream. Accumulates written bytes into a
 * growable array. toByteArray() returns a fresh copy of the written bytes and
 * toString() decodes them as latin1 (each byte 0..255 becomes one char).
 */
public class ByteArrayOutputStream extends OutputStream {

    private byte[] buf;
    private int count;

    public ByteArrayOutputStream() {
        this.buf = new byte[32];
        this.count = 0;
    }

    public ByteArrayOutputStream(int size) {
        this.buf = new byte[size < 1 ? 1 : size];
        this.count = 0;
    }

    // java.util.Arrays has no byte[] copyOf here, so grow by hand.
    private static byte[] grow(byte[] src, int newLength) {
        byte[] dst = new byte[newLength];
        int n = src.length < newLength ? src.length : newLength;
        for (int i = 0; i < n; i++) {
            dst[i] = src[i];
        }
        return dst;
    }

    private void ensure(int minCapacity) {
        if (minCapacity <= buf.length) {
            return;
        }
        int newCap = buf.length * 2;
        if (newCap < minCapacity) {
            newCap = minCapacity;
        }
        buf = grow(buf, newCap);
    }

    public void write(int b) throws IOException {
        ensure(count + 1);
        buf[count] = (byte) b;
        count += 1;
    }

    public void write(byte[] b, int off, int len) throws IOException {
        ensure(count + len);
        for (int i = 0; i < len; i++) {
            buf[count + i] = b[off + i];
        }
        count += len;
    }

    public byte[] toByteArray() {
        return grow(buf, count);
    }

    public int size() {
        return count;
    }

    public void reset() {
        count = 0;
    }

    public String toString() {
        char[] chars = new char[count];
        for (int i = 0; i < count; i++) {
            chars[i] = (char) (buf[i] & 0xFF);
        }
        return new String(chars);
    }
}
