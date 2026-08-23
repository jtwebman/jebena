package java.util;

public final class HexFormat {

    private static final char[] LOWER = {
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };
    private static final char[] UPPER = {
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };

    private static final HexFormat HEX_FORMAT =
        new HexFormat("", "", "", false);

    private final String delimiter;
    private final String prefix;
    private final String suffix;
    private final boolean upperCase;

    private HexFormat(String delimiter, String prefix, String suffix,
                      boolean upperCase) {
        this.delimiter = delimiter;
        this.prefix = prefix;
        this.suffix = suffix;
        this.upperCase = upperCase;
    }

    public static HexFormat of() {
        return HEX_FORMAT;
    }

    public static HexFormat ofDelimiter(String delimiter) {
        Objects.requireNonNull(delimiter, "delimiter");
        return new HexFormat(delimiter, "", "", false);
    }

    public HexFormat withDelimiter(String delimiter) {
        Objects.requireNonNull(delimiter, "delimiter");
        return new HexFormat(delimiter, this.prefix, this.suffix,
                             this.upperCase);
    }

    public HexFormat withPrefix(String prefix) {
        Objects.requireNonNull(prefix, "prefix");
        return new HexFormat(this.delimiter, prefix, this.suffix,
                             this.upperCase);
    }

    public HexFormat withSuffix(String suffix) {
        Objects.requireNonNull(suffix, "suffix");
        return new HexFormat(this.delimiter, this.prefix, suffix,
                             this.upperCase);
    }

    public HexFormat withUpperCase() {
        if (this.upperCase) {
            return this;
        }
        return new HexFormat(this.delimiter, this.prefix, this.suffix, true);
    }

    public HexFormat withLowerCase() {
        if (!this.upperCase) {
            return this;
        }
        return new HexFormat(this.delimiter, this.prefix, this.suffix, false);
    }

    public String delimiter() {
        return delimiter;
    }

    public String prefix() {
        return prefix;
    }

    public String suffix() {
        return suffix;
    }

    public boolean isUpperCase() {
        return upperCase;
    }

    private char[] digits() {
        return upperCase ? UPPER : LOWER;
    }

    public char toLowHexDigit(int value) {
        return digits()[value & 0xf];
    }

    public char toHighHexDigit(int value) {
        return digits()[(value >> 4) & 0xf];
    }

    public String formatHex(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        return formatHex(bytes, 0, bytes.length);
    }

    public String formatHex(byte[] bytes, int fromIndex, int toIndex) {
        Objects.requireNonNull(bytes, "bytes");
        if (fromIndex < 0 || toIndex > bytes.length || fromIndex > toIndex) {
            throw new IndexOutOfBoundsException(
                "fromIndex " + fromIndex + ", toIndex " + toIndex
                + ", length " + bytes.length);
        }
        char[] digits = digits();
        StringBuilder sb = new StringBuilder();
        for (int i = fromIndex; i < toIndex; i++) {
            if (i > fromIndex) {
                sb.append(delimiter);
            }
            sb.append(prefix);
            int v = bytes[i] & 0xff;
            sb.append(digits[v >> 4]);
            sb.append(digits[v & 0xf]);
            sb.append(suffix);
        }
        return sb.toString();
    }

    public byte[] parseHex(CharSequence string) {
        Objects.requireNonNull(string, "string");
        return parseHex(string, 0, string.length());
    }

    public byte[] parseHex(CharSequence string, int fromIndex, int toIndex) {
        Objects.requireNonNull(string, "string");
        if (fromIndex < 0 || toIndex > string.length() || fromIndex > toIndex) {
            throw new IndexOutOfBoundsException(
                "fromIndex " + fromIndex + ", toIndex " + toIndex
                + ", length " + string.length());
        }
        int length = toIndex - fromIndex;
        if (delimiter.isEmpty() && prefix.isEmpty() && suffix.isEmpty()) {
            if ((length & 1) != 0) {
                throw new IllegalArgumentException(
                    "string length not even: " + length);
            }
            byte[] result = new byte[length >> 1];
            int p = fromIndex;
            for (int i = 0; i < result.length; i++) {
                int hi = fromHexDigit(string.charAt(p++));
                int lo = fromHexDigit(string.charAt(p++));
                result[i] = (byte) ((hi << 4) | lo);
            }
            return result;
        }

        int stride = prefix.length() + 2 + suffix.length();
        int delimLen = delimiter.length();
        if (length == 0) {
            return new byte[0];
        }
        // length = n * stride + (n - 1) * delimLen
        int denom = stride + delimLen;
        int n = (length + delimLen) / denom;
        if (n <= 0 || n * stride + (n - 1) * delimLen != length) {
            throw new IllegalArgumentException("invalid length: " + length);
        }
        byte[] result = new byte[n];
        int p = fromIndex;
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                if (!matches(string, p, delimiter)) {
                    throw new IllegalArgumentException(
                        "expected delimiter at index " + p);
                }
                p += delimLen;
            }
            if (!matches(string, p, prefix)) {
                throw new IllegalArgumentException(
                    "expected prefix at index " + p);
            }
            p += prefix.length();
            int hi = fromHexDigit(string.charAt(p++));
            int lo = fromHexDigit(string.charAt(p++));
            result[i] = (byte) ((hi << 4) | lo);
            if (!matches(string, p, suffix)) {
                throw new IllegalArgumentException(
                    "expected suffix at index " + p);
            }
            p += suffix.length();
        }
        return result;
    }

    private static boolean matches(CharSequence s, int off, String token) {
        int len = token.length();
        if (off + len > s.length()) {
            return false;
        }
        for (int i = 0; i < len; i++) {
            if (s.charAt(off + i) != token.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    public static int fromHexDigit(int ch) {
        if (ch >= '0' && ch <= '9') {
            return ch - '0';
        }
        if (ch >= 'a' && ch <= 'f') {
            return ch - 'a' + 10;
        }
        if (ch >= 'A' && ch <= 'F') {
            return ch - 'A' + 10;
        }
        throw new NumberFormatException(
            "not a hexadecimal digit: \"" + (char) ch + "\" = " + ch);
    }

    public static boolean isHexDigit(int ch) {
        return (ch >= '0' && ch <= '9')
            || (ch >= 'a' && ch <= 'f')
            || (ch >= 'A' && ch <= 'F');
    }

    public String toHexDigits(byte value) {
        char[] digits = digits();
        int v = value & 0xff;
        char[] r = new char[2];
        r[0] = digits[v >> 4];
        r[1] = digits[v & 0xf];
        return new String(r);
    }

    public String toHexDigits(int value) {
        char[] digits = digits();
        char[] r = new char[8];
        for (int i = 7; i >= 0; i--) {
            r[i] = digits[value & 0xf];
            value >>>= 4;
        }
        return new String(r);
    }

    public String toHexDigits(long value) {
        char[] digits = digits();
        char[] r = new char[16];
        for (int i = 15; i >= 0; i--) {
            r[i] = digits[(int) (value & 0xf)];
            value >>>= 4;
        }
        return new String(r);
    }

    public String toHexDigits(short value) {
        char[] digits = digits();
        int v = value & 0xffff;
        char[] r = new char[4];
        for (int i = 3; i >= 0; i--) {
            r[i] = digits[v & 0xf];
            v >>>= 4;
        }
        return new String(r);
    }

    public String toHexDigits(char value) {
        return toHexDigits((short) value);
    }

    public String toString() {
        return "uppercase: " + upperCase
            + ", delimiter: \"" + delimiter
            + "\", prefix: \"" + prefix
            + "\", suffix: \"" + suffix + "\"";
    }
}
