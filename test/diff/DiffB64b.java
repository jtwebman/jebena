import java.util.Base64;

public class DiffB64b {

    private static byte[] seq(int n) {
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) {
            b[i] = (byte) ((i * 37 + 11) & 0xff);
        }
        return b;
    }

    private static int csumBytes(byte[] b) {
        int acc = b.length;
        for (int i = 0; i < b.length; i++) {
            acc = acc * 31 + (b[i] & 0xff);
        }
        return acc;
    }

    private static int csumStr(String s) {
        int acc = s.length();
        for (int i = 0; i < s.length(); i++) {
            acc = acc * 31 + s.charAt(i);
        }
        return acc;
    }

    private static int countChar(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) n++;
        }
        return n;
    }

    // Basic encoder without padding: must contain no '=' and match real java.
    public static int basicWithoutPad() {
        byte[] in = seq(5); // 5 bytes -> 8 chars unpadded
        String s = Base64.getEncoder().withoutPadding().encodeToString(in);
        return csumStr(s) * 10 + countChar(s, '=');
    }

    // Basic encoder with padding for contrast.
    public static int basicWithPad() {
        byte[] in = seq(5);
        String s = Base64.getEncoder().encodeToString(in);
        return csumStr(s) * 10 + countChar(s, '=');
    }

    // URL encoder without padding on bytes that produce URL-safe chars.
    public static int urlWithoutPad() {
        byte[] in = {(byte) 0xfb, (byte) 0xff, (byte) 0xbf, (byte) 0xf0, (byte) 0x0f};
        String s = Base64.getUrlEncoder().withoutPadding().encodeToString(in);
        int dash = countChar(s, '-');
        int under = countChar(s, '_');
        int eq = countChar(s, '=');
        return ((csumStr(s) * 10 + dash) * 10 + under) * 10 + eq;
    }

    // URL encoder default (with padding).
    public static int urlDefault() {
        byte[] in = {(byte) 0xfb, (byte) 0xff, (byte) 0xbf, (byte) 0xf0, (byte) 0x0f};
        String s = Base64.getUrlEncoder().encodeToString(in);
        return csumStr(s) * 10 + countChar(s, '=');
    }

    // Custom MIME encoder line length 8 with CRLF separator.
    public static int mimeCustomLines() {
        byte[] in = seq(30); // 40 base64 chars -> lines of 8
        String s = Base64.getMimeEncoder(8, new byte[]{'\r', '\n'}).encodeToString(in);
        int cr = countChar(s, '\r');
        int lf = countChar(s, '\n');
        return (s.length() * 100 + cr) * 100 + lf;
    }

    // Custom MIME encoder checksum.
    public static int mimeCustomChecksum() {
        byte[] in = seq(30);
        String s = Base64.getMimeEncoder(8, new byte[]{'\r', '\n'}).encodeToString(in);
        return csumStr(s);
    }

    // MIME line length rounds down to a multiple of 4 (10 -> 8).
    public static int mimeLineRounding() {
        byte[] in = seq(20);
        String s = Base64.getMimeEncoder(10, new byte[]{'\n'}).encodeToString(in);
        int lf = countChar(s, '\n');
        return s.length() * 100 + lf;
    }

    // lineLength <= 0 -> no line wrapping (basic encoder).
    public static int mimeZeroLine() {
        byte[] in = seq(30);
        String s = Base64.getMimeEncoder(0, new byte[]{'\r', '\n'}).encodeToString(in);
        int cr = countChar(s, '\r');
        return (csumStr(s) * 10 + cr);
    }

    // Illegal separator char (contains a base64 char) throws.
    public static int mimeIllegalSeparator() {
        try {
            Base64.getMimeEncoder(8, new byte[]{'A'});
            return -1;
        } catch (IllegalArgumentException e) {
            return 42;
        }
    }

    // encode(byte[]) returning byte[] -> checksum.
    public static int encodeBytesChecksum() {
        byte[] in = seq(17);
        byte[] out = Base64.getEncoder().encode(in);
        return csumBytes(out);
    }

    // Decode a no-padding string with the basic decoder (lenient of missing pad).
    public static int decodeNoPad() {
        byte[] in = seq(5);
        String noPad = Base64.getEncoder().withoutPadding().encodeToString(in);
        byte[] back = Base64.getDecoder().decode(noPad);
        return csumBytes(back);
    }

    // Round trip basic: encode then decode returns original.
    public static int roundTripBasic() {
        byte[] in = seq(23);
        String s = Base64.getEncoder().encodeToString(in);
        byte[] back = Base64.getDecoder().decode(s);
        boolean eq = back.length == in.length;
        for (int i = 0; eq && i < in.length; i++) {
            if (back[i] != in[i]) eq = false;
        }
        return (eq ? 1 : 0) * 1000000 + csumBytes(back);
    }

    // Round trip url without padding.
    public static int roundTripUrlNoPad() {
        byte[] in = seq(19);
        String s = Base64.getUrlEncoder().withoutPadding().encodeToString(in);
        byte[] back = Base64.getUrlDecoder().decode(s);
        boolean eq = back.length == in.length;
        for (int i = 0; eq && i < in.length; i++) {
            if (back[i] != in[i]) eq = false;
        }
        return (eq ? 1 : 0) * 1000000 + csumBytes(back);
    }
}
