package java.util.zip;

/**
 * Standard CRC-32 checksum (reflected polynomial 0xEDB88320, initial value
 * 0xFFFFFFFF, final one's-complement). Clean-room implementation.
 */
public class CRC32 {

    private static final int[] TABLE = buildTable();

    private int crc = 0;

    public CRC32() {
    }

    private static int[] buildTable() {
        int[] t = new int[256];
        for (int n = 0; n < 256; n++) {
            int c = n;
            for (int k = 0; k < 8; k++) {
                if ((c & 1) != 0) {
                    c = 0xEDB88320 ^ (c >>> 1);
                } else {
                    c = c >>> 1;
                }
            }
            t[n] = c;
        }
        return t;
    }

    public void update(int b) {
        int c = ~crc;
        c = TABLE[(c ^ b) & 0xff] ^ (c >>> 8);
        crc = ~c;
    }

    public void update(byte[] b) {
        update(b, 0, b.length);
    }

    public void update(byte[] b, int off, int len) {
        if (b == null) {
            throw new NullPointerException();
        }
        if (off < 0 || len < 0 || off > b.length - len) {
            throw new ArrayIndexOutOfBoundsException();
        }
        int c = ~crc;
        int end = off + len;
        for (int i = off; i < end; i++) {
            c = TABLE[(c ^ b[i]) & 0xff] ^ (c >>> 8);
        }
        crc = ~c;
    }

    public long getValue() {
        return ((long) crc) & 0xffffffffL;
    }

    public void reset() {
        crc = 0;
    }
}
