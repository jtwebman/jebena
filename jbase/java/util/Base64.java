package java.util;

/**
 * Original clean-room implementation of the RFC 4648 Base64 codec, providing
 * the Basic and URL/filename-safe variants exposed through the standard
 * java.util.Base64 factory methods.
 */
public class Base64 {

    private Base64() {
    }

    // RFC 4648 Table 1 (Basic): A-Z a-z 0-9 + /
    private static final char[] BASIC = toChars(
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/");

    // RFC 4648 Table 2 (URL and filename safe): identical except + -> - and / -> _
    private static final char[] URLSAFE = toChars(
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_");

    private static char[] toChars(String s) {
        char[] c = new char[s.length()];
        for (int i = 0; i < s.length(); i++) {
            c[i] = s.charAt(i);
        }
        return c;
    }

    private static final Encoder BASIC_ENCODER = new Encoder(BASIC, true);
    private static final Encoder BASIC_ENCODER_NP = new Encoder(BASIC, false);
    private static final Encoder URL_ENCODER = new Encoder(URLSAFE, true);
    private static final Encoder URL_ENCODER_NP = new Encoder(URLSAFE, false);

    private static final Decoder BASIC_DECODER = new Decoder(BASIC);
    private static final Decoder URL_DECODER = new Decoder(URLSAFE);

    public static Encoder getEncoder() {
        return BASIC_ENCODER;
    }

    public static Decoder getDecoder() {
        return BASIC_DECODER;
    }

    public static Encoder getUrlEncoder() {
        return URL_ENCODER;
    }

    public static Decoder getUrlDecoder() {
        return URL_DECODER;
    }

    public static class Encoder {

        private final char[] alphabet;
        private final boolean padding;

        private Encoder(char[] alphabet, boolean padding) {
            this.alphabet = alphabet;
            this.padding = padding;
        }

        public Encoder withoutPadding() {
            if (!padding) {
                return this;
            }
            if (alphabet == BASIC) {
                return BASIC_ENCODER_NP;
            }
            return URL_ENCODER_NP;
        }

        public byte[] encode(byte[] src) {
            if (src == null) {
                throw new NullPointerException();
            }
            int len = src.length;
            int groups = len / 3;
            int rem = len - groups * 3;
            int outLen = groups * 4;
            if (rem != 0) {
                outLen += padding ? 4 : (rem + 1);
            }
            byte[] out = new byte[outLen];
            int si = 0;
            int di = 0;
            for (int g = 0; g < groups; g++) {
                int b0 = src[si++] & 0xff;
                int b1 = src[si++] & 0xff;
                int b2 = src[si++] & 0xff;
                int bits = (b0 << 16) | (b1 << 8) | b2;
                out[di++] = (byte) alphabet[(bits >>> 18) & 0x3f];
                out[di++] = (byte) alphabet[(bits >>> 12) & 0x3f];
                out[di++] = (byte) alphabet[(bits >>> 6) & 0x3f];
                out[di++] = (byte) alphabet[bits & 0x3f];
            }
            if (rem == 1) {
                int b0 = src[si++] & 0xff;
                out[di++] = (byte) alphabet[(b0 >>> 2) & 0x3f];
                out[di++] = (byte) alphabet[(b0 << 4) & 0x3f];
                if (padding) {
                    out[di++] = (byte) '=';
                    out[di++] = (byte) '=';
                }
            } else if (rem == 2) {
                int b0 = src[si++] & 0xff;
                int b1 = src[si++] & 0xff;
                out[di++] = (byte) alphabet[(b0 >>> 2) & 0x3f];
                out[di++] = (byte) alphabet[((b0 << 4) | (b1 >>> 4)) & 0x3f];
                out[di++] = (byte) alphabet[(b1 << 2) & 0x3f];
                if (padding) {
                    out[di++] = (byte) '=';
                }
            }
            return out;
        }

        public String encodeToString(byte[] src) {
            byte[] encoded = encode(src);
            char[] chars = new char[encoded.length];
            for (int i = 0; i < encoded.length; i++) {
                chars[i] = (char) (encoded[i] & 0xff);
            }
            return new String(chars);
        }
    }

    public static class Decoder {

        private final int[] lookup;

        private Decoder(char[] alphabet) {
            int[] table = new int[128];
            for (int i = 0; i < table.length; i++) {
                table[i] = -1;
            }
            for (int i = 0; i < alphabet.length; i++) {
                table[alphabet[i]] = i;
            }
            this.lookup = table;
        }

        public byte[] decode(String src) {
            if (src == null) {
                throw new NullPointerException();
            }
            int len = src.length();
            byte[] bytes = new byte[len];
            for (int i = 0; i < len; i++) {
                bytes[i] = (byte) src.charAt(i);
            }
            return decode(bytes);
        }

        public byte[] decode(byte[] src) {
            if (src == null) {
                throw new NullPointerException();
            }
            int len = src.length;

            // First pass: determine payload length excluding padding, validating.
            int symbols = 0;
            int pad = 0;
            for (int i = 0; i < len; i++) {
                int c = src[i] & 0xff;
                if (c == '=') {
                    pad++;
                    continue;
                }
                if (pad != 0) {
                    throw new IllegalArgumentException(
                            "Input byte array has incorrect ending byte at " + i);
                }
                if (c >= 128 || lookup[c] < 0) {
                    throw new IllegalArgumentException(
                            "Illegal base64 character " + Integer.toHexString(c));
                }
                symbols++;
            }

            int rem = symbols % 4;
            if (rem == 1) {
                throw new IllegalArgumentException(
                        "Input byte array has wrong 4-byte ending unit");
            }

            int groups = symbols / 4;
            int outLen = groups * 3;
            if (rem == 2) {
                outLen += 1;
            } else if (rem == 3) {
                outLen += 2;
            }
            byte[] out = new byte[outLen];

            int di = 0;
            int bits = 0;
            int count = 0;
            for (int i = 0; i < len; i++) {
                int c = src[i] & 0xff;
                if (c == '=') {
                    break;
                }
                bits = (bits << 6) | lookup[c];
                count++;
                if (count == 4) {
                    out[di++] = (byte) (bits >>> 16);
                    out[di++] = (byte) (bits >>> 8);
                    out[di++] = (byte) bits;
                    bits = 0;
                    count = 0;
                }
            }
            if (count == 2) {
                // 12 bits -> top 8 bits are one byte
                out[di++] = (byte) (bits >>> 4);
            } else if (count == 3) {
                // 18 bits -> top 16 bits are two bytes
                out[di++] = (byte) (bits >>> 10);
                out[di++] = (byte) (bits >>> 2);
            }
            return out;
        }
    }
}
