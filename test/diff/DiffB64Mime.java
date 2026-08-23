import java.util.Base64;

public class DiffB64Mime {

    private static byte[] seq(int n) {
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) {
            b[i] = (byte) ((i * 37 + 11) & 0xff);
        }
        return b;
    }

    private static int sum(byte[] b) {
        int acc = 0;
        for (int i = 0; i < b.length; i++) {
            acc = acc * 31 + (b[i] & 0xff);
        }
        return acc;
    }

    // Encoded length of a 120-byte input (forces multiple 76-char lines).
    public static int mimeEncodedLength() {
        byte[] in = seq(120);
        String s = Base64.getMimeEncoder().encodeToString(in);
        return s.length();
    }

    // Count of CR characters in the encoded output (one per line break).
    public static int mimeCrCount() {
        byte[] in = seq(120);
        String s = Base64.getMimeEncoder().encodeToString(in);
        int c = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\r') c++;
        }
        return c;
    }

    // Count of LF characters in the encoded output.
    public static int mimeLfCount() {
        byte[] in = seq(120);
        String s = Base64.getMimeEncoder().encodeToString(in);
        int c = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') c++;
        }
        return c;
    }

    // Length of the first line == 76.
    public static int mimeFirstLineLength() {
        byte[] in = seq(200);
        String s = Base64.getMimeEncoder().encodeToString(in);
        int idx = s.indexOf('\r');
        return idx;
    }

    // Every full line (between separators) must be 76 chars; return 1 if so.
    public static int mimeLineLengthsOk() {
        byte[] in = seq(200);
        String s = Base64.getMimeEncoder().encodeToString(in);
        String[] parts = s.split("\r\n");
        boolean ok = true;
        for (int i = 0; i < parts.length - 1; i++) {
            if (parts[i].length() != 76) ok = false;
        }
        // last part must be <= 76
        if (parts.length > 0 && parts[parts.length - 1].length() > 76) ok = false;
        return ok ? 1 : 0;
    }

    // Checksum of the full encoded string's chars.
    public static int mimeEncodedChecksum() {
        byte[] in = seq(120);
        String s = Base64.getMimeEncoder().encodeToString(in);
        int acc = 0;
        for (int i = 0; i < s.length(); i++) {
            acc = acc * 31 + s.charAt(i);
        }
        return acc;
    }

    // Round-trip a ~200-byte input through mime encoder/decoder; byte sum.
    public static int mimeRoundTripSum() {
        byte[] in = seq(200);
        String s = Base64.getMimeEncoder().encodeToString(in);
        byte[] out = Base64.getMimeDecoder().decode(s);
        return sum(out);
    }

    // Round-trip length must equal original.
    public static int mimeRoundTripLength() {
        byte[] in = seq(200);
        String s = Base64.getMimeEncoder().encodeToString(in);
        byte[] out = Base64.getMimeDecoder().decode(s);
        return out.length;
    }

    // Decoder must ignore embedded whitespace/newlines not produced by encoder.
    public static int mimeDecodeIgnoresWhitespace() {
        byte[] in = seq(30);
        String s = Base64.getEncoder().encodeToString(in);
        // sprinkle spaces, tabs, and newlines throughout
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            sb.append(s.charAt(i));
            if (i % 3 == 0) sb.append(' ');
            if (i % 5 == 0) sb.append("\r\n");
            if (i % 7 == 0) sb.append('\t');
        }
        byte[] out = Base64.getMimeDecoder().decode(sb.toString());
        return sum(out) * 100 + out.length;
    }

    // MIME decoder handles a plain (unwrapped) basic string too.
    public static int mimeDecodeBasicString() {
        byte[] in = seq(45);
        String s = Base64.getEncoder().encodeToString(in);
        byte[] out = Base64.getMimeDecoder().decode(s);
        return sum(out) * 100 + out.length;
    }

    // Short input: no line break at all (raw length < 76).
    public static int mimeShortNoBreak() {
        byte[] in = seq(9);
        String s = Base64.getMimeEncoder().encodeToString(in);
        int cr = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\r') cr++;
        }
        return s.length() * 10 + cr;
    }

    // Exactly 57 bytes -> exactly 76 encoded chars -> no trailing separator.
    public static int mimeExact76() {
        byte[] in = seq(57);
        String s = Base64.getMimeEncoder().encodeToString(in);
        int cr = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\r') cr++;
        }
        return s.length() * 10 + cr;
    }
}
