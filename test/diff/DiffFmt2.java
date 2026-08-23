public class DiffFmt2 {
    private static int enc(String s) {
        return s.length() * 1000000 + (s.hashCode() & 0xFFFFF);
    }

    // "%+08d" sign + zero-pad width
    public static int plusZeroWidth() {
        return enc(String.format("%+08d", Integer.valueOf(42)));
    }

    // "%(08d" of a negative
    public static int parenZeroNeg() {
        return enc(String.format("%(08d", Integer.valueOf(-42)));
    }

    // "%+,d"
    public static int plusComma() {
        return enc(String.format("%+,d", Integer.valueOf(1234567)));
    }

    // "% ,d"
    public static int spaceComma() {
        return enc(String.format("% ,d", Integer.valueOf(1234567)));
    }

    // "%-10d|" left-justify width
    public static int leftWidth() {
        return enc(String.format("%-10d|", Integer.valueOf(42)));
    }

    // "%+.3f"
    public static int plusPrecF() {
        return enc(String.format("%+.3f", Double.valueOf(3.14159)));
    }

    // "%(.2f" of a negative
    public static int parenPrecFNeg() {
        return enc(String.format("%(.2f", Double.valueOf(-3.14159)));
    }

    // "%08.2f"
    public static int zeroWidthPrecF() {
        return enc(String.format("%08.2f", Double.valueOf(3.14159)));
    }

    // "%+e" scientific
    public static int plusSci() {
        return enc(String.format("%+e", Double.valueOf(31415.9)));
    }

    // "%,12d" comma + width
    public static int commaWidth() {
        return enc(String.format("%,12d", Integer.valueOf(1234567)));
    }

    // "%(08d" of a negative that grows past the width
    public static int parenZeroNegWide() {
        return enc(String.format("%(012d", Integer.valueOf(-9876543)));
    }

    // "% +" both space and plus (plus wins) with comma
    public static int spacePlusComma() {
        return enc(String.format("%+,d", Integer.valueOf(-7654321)));
    }
}
