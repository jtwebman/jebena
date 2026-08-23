public class DiffIntU2 {
    // Integer.parseUnsignedInt("4294967295") == -1 as bits
    public static int parseMaxAsBits() {
        int v = Integer.parseUnsignedInt("4294967295");
        return v ^ -1; // 0 when equal to all-ones
    }

    public static int parseMaxBitCount() {
        int v = Integer.parseUnsignedInt("4294967295");
        return Integer.bitCount(v); // 32
    }

    // parseUnsignedInt(str, radix)
    public static int parseUnsignedRadixHex() {
        int v = Integer.parseUnsignedInt("ffffffff", 16);
        return v ^ -1; // 0
    }

    public static int parseUnsignedRadixBin() {
        int v = Integer.parseUnsignedInt("11111111111111111111111111111111", 2);
        return Integer.bitCount(v); // 32
    }

    // toUnsignedString(-1) length and hash
    public static int toUnsignedStringLenHash() {
        String s = Integer.toUnsignedString(-1);
        return s.length() * 100000 + (s.hashCode() & 0x7fffffff) % 100000;
    }

    // toUnsignedString(255, 16) -> "ff"
    public static int toUnsignedStringHex255() {
        String s = Integer.toUnsignedString(255, 16);
        return s.length() * 1000 + s.hashCode();
    }

    public static int toUnsignedStringNeg1Hex() {
        String s = Integer.toUnsignedString(-1, 16);
        return s.length() * 100 + s.hashCode();
    }

    // Integer.divideUnsigned(-2,3) and remainderUnsigned(-1,7)
    public static int divRemUnsignedInt() {
        int d = Integer.divideUnsigned(-2, 3);
        int r = Integer.remainderUnsigned(-1, 7);
        return d * 31 + r;
    }

    // toUnsignedLong(-1) low bits
    public static int toUnsignedLongLowBits() {
        long l = Integer.toUnsignedLong(-1);
        return (int) l; // -1 low 32 bits
    }

    public static int toUnsignedLongHighZero() {
        long l = Integer.toUnsignedLong(-1);
        return (int) (l >>> 32); // 0
    }

    // Long.parseUnsignedLong full range
    public static int parseUnsignedLongMax() {
        long v = Long.parseUnsignedLong("18446744073709551615");
        return (int) (v ^ -1L); // 0 when all-ones
    }

    public static int parseUnsignedLongBitCount() {
        long v = Long.parseUnsignedLong("18446744073709551615");
        return Long.bitCount(v); // 64
    }

    public static int parseUnsignedLongRadixHex() {
        long v = Long.parseUnsignedLong("ffffffffffffffff", 16);
        return Long.bitCount(v); // 64
    }

    public static int parseUnsignedLongMid() {
        long v = Long.parseUnsignedLong("9999999999999999999");
        return (int) (v ^ (v >>> 17));
    }

    // Long.toUnsignedString variants
    public static int longToUnsignedStringLenHash() {
        String s = Long.toUnsignedString(-1L);
        return s.length() * 100000 + (s.hashCode() & 0x7fffffff) % 100000;
    }

    public static int longToUnsignedStringHex() {
        String s = Long.toUnsignedString(-1L, 16);
        return s.length() * 100 + s.hashCode();
    }

    public static int longToUnsignedStringOct() {
        String s = Long.toUnsignedString(-1L, 8);
        return s.length() * 100 + s.hashCode();
    }

    // Long divide/remainder unsigned
    public static int longDivRemUnsigned() {
        long d = Long.divideUnsigned(-2L, 3L);
        long r = Long.remainderUnsigned(-1L, 7L);
        return (int) (d * 31 + r);
    }

    // round trip parse then toUnsignedString
    public static int roundTripLong() {
        long v = Long.parseUnsignedLong("12345678901234567890");
        String s = Long.toUnsignedString(v);
        return s.equals("12345678901234567890") ? s.length() : -1;
    }

    // caught exception: leading minus
    public static int parseMinusSentinel() {
        try {
            Integer.parseUnsignedInt("-5");
            return 0;
        } catch (NumberFormatException e) {
            return 42;
        }
    }

    // caught exception: exceeds unsigned int range
    public static int parseTooBigIntSentinel() {
        try {
            Integer.parseUnsignedInt("4294967296");
            return 0;
        } catch (NumberFormatException e) {
            return 7;
        }
    }
}
