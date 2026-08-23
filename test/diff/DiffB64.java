import java.util.Base64;

/**
 * Differential coverage for java.util.Base64: Basic + URL-safe encode/decode,
 * padding cases ("Man"/"Ma"/"M"), roundtrip, +// -> -_ substitution, and
 * withoutPadding. Each method returns a deterministic int (string checksum or
 * byte checksum acc=acc*31+(v&0xff)) checked byte-for-byte vs real java.
 */
public class DiffB64 {

    private static int checksum(String s) {
        int acc = 0;
        for (int i = 0; i < s.length(); i++) {
            acc = acc * 31 + s.charAt(i);
        }
        return acc;
    }

    private static int checksum(byte[] b) {
        int acc = 0;
        for (int i = 0; i < b.length; i++) {
            acc = acc * 31 + (b[i] & 0xff);
        }
        return acc;
    }

    private static byte[] bytes(String s) {
        byte[] b = new byte[s.length()];
        for (int i = 0; i < s.length(); i++) {
            b[i] = (byte) s.charAt(i);
        }
        return b;
    }

    // "Man" -> "TWFu" (no padding, 3 bytes)
    static int encMan() {
        return checksum(Base64.getEncoder().encodeToString(bytes("Man")));
    }

    // "Ma" -> "TWE=" (one pad)
    static int encMa() {
        return checksum(Base64.getEncoder().encodeToString(bytes("Ma")));
    }

    // "M" -> "TQ==" (two pads)
    static int encM() {
        return checksum(Base64.getEncoder().encodeToString(bytes("M")));
    }

    // "hello world" -> "aGVsbG8gd29ybGQ=" (one pad)
    static int encHello() {
        return checksum(Base64.getEncoder().encodeToString(bytes("hello world")));
    }

    // empty input -> "" (length 0, checksum 0)
    static int encEmpty() {
        return checksum(Base64.getEncoder().encodeToString(new byte[0])) + 1;
    }

    // roundtrip: decode(encode(x)) == x for a full-byte-range sample
    static int roundtrip() {
        byte[] src = new byte[256];
        for (int i = 0; i < 256; i++) {
            src[i] = (byte) i;
        }
        byte[] enc = Base64.getEncoder().encode(src);
        byte[] dec = Base64.getDecoder().decode(enc);
        if (dec.length != src.length) {
            return -1;
        }
        for (int i = 0; i < src.length; i++) {
            if (dec[i] != src[i]) {
                return -2;
            }
        }
        return checksum(dec);
    }

    // decode from String path
    static int decString() {
        byte[] dec = Base64.getDecoder().decode("aGVsbG8gd29ybGQ=");
        return checksum(dec);
    }

    // URL-safe: bytes {0xfb, 0xff, 0xbf} yield +// in basic -> -_ in url-safe.
    // basic("ûÿ¿") -> "+/+/", url-safe -> "-_-_"
    static int urlSafeChars() {
        byte[] src = new byte[] { (byte) 0xfb, (byte) 0xff, (byte) 0xbf };
        String basic = Base64.getEncoder().encodeToString(src);
        String url = Base64.getUrlEncoder().encodeToString(src);
        return checksum(basic) * 31 + checksum(url);
    }

    // URL-safe roundtrip
    static int urlRoundtrip() {
        byte[] src = new byte[] { (byte) 0xfb, (byte) 0xff, (byte) 0xbf, (byte) 0x00, (byte) 0x10 };
        String url = Base64.getUrlEncoder().encodeToString(src);
        byte[] dec = Base64.getUrlDecoder().decode(url);
        return checksum(url) * 31 + checksum(dec);
    }

    // withoutPadding: "Ma" -> "TWE" (no trailing =)
    static int noPad() {
        String p = Base64.getEncoder().encodeToString(bytes("Ma"));
        String np = Base64.getEncoder().withoutPadding().encodeToString(bytes("Ma"));
        return p.length() * 1000 + np.length() * 10 + checksum(np) % 1000;
    }

    // decode of unpadded input works too
    static int decNoPad() {
        byte[] a = Base64.getDecoder().decode("TWE");
        byte[] b = Base64.getDecoder().decode("TWE=");
        if (a.length != b.length) {
            return -1;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return -2;
            }
        }
        return checksum(a);
    }
}
