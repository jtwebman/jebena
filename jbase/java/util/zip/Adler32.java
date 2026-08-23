package java.util.zip;

/**
 * Adler-32 checksum (two rolling 16-bit sums modulo 65521). Clean-room
 * implementation.
 */
public class Adler32 {

    private static final int BASE = 65521;

    private int a = 1;
    private int b = 0;

    public Adler32() {
    }

    public void update(int val) {
        int s1 = a;
        int s2 = b;
        s1 = (s1 + (val & 0xff)) % BASE;
        s2 = (s2 + s1) % BASE;
        a = s1;
        b = s2;
    }

    public void update(byte[] buf) {
        update(buf, 0, buf.length);
    }

    public void update(byte[] buf, int off, int len) {
        if (buf == null) {
            throw new NullPointerException();
        }
        if (off < 0 || len < 0 || off > buf.length - len) {
            throw new ArrayIndexOutOfBoundsException();
        }
        int s1 = a;
        int s2 = b;
        int end = off + len;
        for (int i = off; i < end; i++) {
            s1 = (s1 + (buf[i] & 0xff)) % BASE;
            s2 = (s2 + s1) % BASE;
        }
        a = s1;
        b = s2;
    }

    public long getValue() {
        return ((long) ((b << 16) | a)) & 0xffffffffL;
    }

    public void reset() {
        a = 1;
        b = 0;
    }
}
