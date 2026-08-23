import java.util.zip.Adler32;
import java.util.zip.CRC32;

/**
 * Differential coverage for java.util.zip.CRC32 and Adler32: single-shot value,
 * incremental (two-half) update equivalence, single-byte update, and reset()
 * identity value. Bytes are built from ASCII via charAt to avoid String.getBytes
 * (charset machinery). Every method returns a deterministic int checked
 * byte-for-byte vs real java.
 */
public class DiffCrc {

    private static byte[] ascii(String s) {
        byte[] out = new byte[s.length()];
        for (int i = 0; i < s.length(); i++) {
            out[i] = (byte) (s.charAt(i) & 0xff);
        }
        return out;
    }

    private static final byte[] DATA = ascii("hello world");

    static int crcHello() {
        CRC32 c = new CRC32();
        c.update(DATA);
        return (int) c.getValue();
    }

    static int adlerHello() {
        Adler32 a = new Adler32();
        a.update(DATA);
        return (int) a.getValue();
    }

    static int crcIncremental() {
        CRC32 whole = new CRC32();
        whole.update(DATA);
        CRC32 split = new CRC32();
        split.update(DATA, 0, 5);
        split.update(DATA, 5, DATA.length - 5);
        return whole.getValue() == split.getValue() ? 1 : 0;
    }

    static int adlerIncremental() {
        Adler32 whole = new Adler32();
        whole.update(DATA);
        Adler32 split = new Adler32();
        split.update(DATA, 0, 5);
        split.update(DATA, 5, DATA.length - 5);
        return whole.getValue() == split.getValue() ? 1 : 0;
    }

    static int crcSingleBytes() {
        CRC32 bytes = new CRC32();
        for (int i = 0; i < DATA.length; i++) {
            bytes.update(DATA[i]);
        }
        CRC32 whole = new CRC32();
        whole.update(DATA);
        return bytes.getValue() == whole.getValue() ? 1 : 0;
    }

    static int adlerSingleBytes() {
        Adler32 bytes = new Adler32();
        for (int i = 0; i < DATA.length; i++) {
            bytes.update(DATA[i]);
        }
        Adler32 whole = new Adler32();
        whole.update(DATA);
        return bytes.getValue() == whole.getValue() ? 1 : 0;
    }

    static int crcReset() {
        CRC32 c = new CRC32();
        c.update(DATA);
        c.reset();
        return (int) c.getValue(); // 0
    }

    static int adlerReset() {
        Adler32 a = new Adler32();
        a.update(DATA);
        a.reset();
        return (int) a.getValue(); // 1
    }

    static int crcEmpty() {
        CRC32 c = new CRC32();
        c.update(new byte[0]);
        return (int) c.getValue(); // 0
    }

    static int adlerRange() {
        // sub-range: bytes [6,11) == "world"
        Adler32 a = new Adler32();
        a.update(DATA, 6, 5);
        Adler32 b = new Adler32();
        b.update(ascii("world"));
        return a.getValue() == b.getValue() ? 1 : 0;
    }
}
