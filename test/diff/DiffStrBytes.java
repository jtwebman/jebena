/**
 * Differential coverage for the new UTF-8 String<->byte bridge: getBytes() and
 * new String(byte[]) / new String(byte[],int,int). Covers ASCII, 2-byte (U+00E9 e-acute),
 * 3-byte (U+20AC euro), roundtrips, and offset decode. Each returns a deterministic int
 * checked byte-for-byte vs real java (whose default charset is UTF-8).
 */
public class DiffStrBytes {

    static int ck(byte[] b) {
        int a = 0;
        for (int i = 0; i < b.length; i++) {
            a = a * 31 + (b[i] & 0xff);
        }
        return a;
    }

    static int ck(String s) {
        int a = 0;
        for (int i = 0; i < s.length(); i++) {
            a = a * 31 + s.charAt(i);
        }
        return a;
    }

    public static int asciiLen() {
        return "hello world".getBytes().length; // 11
    }

    public static int asciiBytes() {
        return ck("Base64!".getBytes());
    }

    public static int twoByteLen() {
        return "café".getBytes().length; // c,a,f = 3 + e-acute(2) = 5
    }

    public static int twoByteBytes() {
        return ck("café".getBytes());
    }

    public static int threeByteLen() {
        return "€10".getBytes().length; // euro(3) + '1','0' = 5
    }

    public static int threeByteBytes() {
        return ck("€5".getBytes());
    }

    public static int roundtripAscii() {
        byte[] b = "The quick brown fox".getBytes();
        return ck(new String(b)); // decode back
    }

    public static int roundtripUtf8() {
        String s = "é€ü café"; // mixed multibyte
        return ck(new String(s.getBytes()));
    }

    public static int offsetDecode() {
        byte[] b = "abcdef".getBytes();
        return ck(new String(b, 2, 3)); // "cde"
    }

    public static int emptyBytes() {
        return "".getBytes().length + ck(new String(new byte[0])); // 0
    }
}
