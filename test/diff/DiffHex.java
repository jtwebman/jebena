import java.util.HexFormat;

public class DiffHex {

    static int cs(String s) {
        int acc = 0;
        for (int i = 0; i < s.length(); i++) {
            acc = acc * 31 + s.charAt(i);
        }
        return acc;
    }

    static int cs(byte[] b) {
        int acc = 0;
        for (int i = 0; i < b.length; i++) {
            acc = acc * 31 + (b[i] & 0xff);
        }
        return acc;
    }

    public static int formatLower() {
        byte[] b = { (byte) 0x0f, (byte) 0xa0, (byte) 0xff, (byte) 0x00 };
        return cs(HexFormat.of().formatHex(b));
    }

    public static int formatUpper() {
        byte[] b = { (byte) 0x0f, (byte) 0xa0, (byte) 0xff, (byte) 0x00 };
        return cs(HexFormat.of().withUpperCase().formatHex(b));
    }

    public static int parseRoundtrip() {
        byte[] b = HexFormat.of().parseHex("0fa0ff");
        return cs(b);
    }

    public static int parseFormatRoundtrip() {
        byte[] b = { (byte) 0x0f, (byte) 0xa0, (byte) 0xff, (byte) 0x00 };
        String s = HexFormat.of().formatHex(b);
        byte[] back = HexFormat.of().parseHex(s);
        return cs(back);
    }

    public static int toHexDigitsInt255() {
        return cs(HexFormat.of().toHexDigits(255));
    }

    public static int toHexDigitsInt1234() {
        return cs(HexFormat.of().toHexDigits(0x1234));
    }

    public static int toHexDigitsLong() {
        return cs(HexFormat.of().toHexDigits(255L));
    }

    public static int toHexDigitsByte() {
        return cs(HexFormat.of().toHexDigits((byte) 0xab));
    }

    public static int delimiterFormat() {
        byte[] b = { (byte) 0x0f, (byte) 0xa0, (byte) 0xff, (byte) 0x00 };
        return cs(HexFormat.ofDelimiter(":").formatHex(b));
    }

    public static int delimiterRoundtrip() {
        byte[] b = HexFormat.ofDelimiter(":").parseHex("0f:a0:ff:00");
        return cs(b);
    }

    public static int lowHighDigit() {
        HexFormat h = HexFormat.of();
        int acc = 0;
        acc = acc * 31 + h.toLowHexDigit(0xab);
        acc = acc * 31 + h.toHighHexDigit(0xab);
        acc = acc * 31 + h.withUpperCase().toHighHexDigit(0xcd);
        return acc;
    }
}
