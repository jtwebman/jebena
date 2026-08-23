public class DiffIntRadix {
    public static int parseHex() {
        return Integer.parseInt("ff", 16); // 255
    }

    public static int parseBinNeg() {
        return Integer.parseInt("-101", 2); // -5
    }

    public static int parseVariousRadix() {
        int acc = 0;
        acc = acc * 31 + Integer.parseInt("z", 36);      // 35
        acc = acc * 31 + Integer.parseInt("+7fff", 16);  // 32767
        acc = acc * 31 + Integer.parseInt("777", 8);     // 511
        acc = acc * 31 + Integer.parseInt("-80000000", 16); // MIN_VALUE
        return acc;
    }

    public static int toStringRadix() {
        int acc = 0;
        acc = acc * 31 + Integer.toString(255, 16).hashCode();      // "ff"
        acc = acc * 31 + Integer.toString(-255, 16).hashCode();     // "-ff"
        acc = acc * 31 + Integer.toString(Integer.MIN_VALUE, 16).hashCode();
        acc = acc * 31 + Integer.toString(35, 36).hashCode();       // "z"
        acc = acc * 31 + Integer.toString(511, 8).hashCode();       // "777"
        return acc;
    }

    public static int radixStringLengths() {
        int acc = 0;
        acc = acc * 100 + Integer.toBinaryString(255).length(); // 8
        acc = acc * 100 + Integer.toHexString(255).length();    // 2
        acc = acc * 100 + Integer.toOctalString(255).length();  // 3
        acc = acc * 100 + Integer.toBinaryString(-1).length();  // 32
        acc = acc * 100 + Integer.toHexString(-1).length();     // 8
        return acc;
    }

    public static int radixStringHashes() {
        int acc = 0;
        acc = acc * 31 + Integer.toBinaryString(255).hashCode();
        acc = acc * 31 + Integer.toHexString(0xdeadbeef).hashCode();
        acc = acc * 31 + Integer.toOctalString(511).hashCode();
        acc = acc * 31 + Integer.toBinaryString(0).hashCode();
        return acc;
    }

    public static int bitCounts() {
        int acc = 0;
        acc = acc * 100 + Integer.bitCount(255);        // 8
        acc = acc * 100 + Integer.bitCount(-1);         // 32
        acc = acc * 100 + Integer.bitCount(0);          // 0
        acc = acc * 100 + Integer.bitCount(0x80000000); // 1
        acc = acc * 100 + Integer.bitCount(0xdeadbeef);
        return acc;
    }

    public static int highLowBits() {
        int acc = 0;
        acc = acc * 31 + Integer.highestOneBit(255);   // 128
        acc = acc * 31 + Integer.lowestOneBit(12);     // 4
        acc = acc * 31 + Integer.highestOneBit(0);     // 0
        acc = acc * 31 + Integer.lowestOneBit(0);      // 0
        acc = acc * 31 + Integer.highestOneBit(-1);    // MIN_VALUE
        return acc;
    }

    public static int leadingTrailingZeros() {
        int acc = 0;
        acc = acc * 100 + Integer.numberOfLeadingZeros(1);   // 31
        acc = acc * 100 + Integer.numberOfLeadingZeros(0);   // 32
        acc = acc * 100 + Integer.numberOfLeadingZeros(255); // 24
        acc = acc * 100 + Integer.numberOfTrailingZeros(8);  // 3
        acc = acc * 100 + Integer.numberOfTrailingZeros(0);  // 32
        acc = acc * 100 + Integer.numberOfTrailingZeros(-2147483648); // 31
        return acc;
    }

    public static int reverseOps() {
        int acc = 0;
        acc = acc * 31 + Integer.reverse(1);           // MIN_VALUE
        acc = acc * 31 + Integer.reverse(0xdeadbeef);
        acc = acc * 31 + Integer.reverseBytes(0x01020304); // 0x04030201
        acc = acc * 31 + Integer.reverseBytes(0xdeadbeef);
        return acc;
    }

    public static int longRadix() {
        int acc = 0;
        acc = acc * 31 + (int) Long.parseLong("ff", 16);        // 255
        acc = acc * 31 + (int) Long.parseLong("-101", 2);       // -5
        acc = acc * 31 + Long.toHexString(255L).hashCode();     // "ff"
        acc = acc * 31 + Long.toHexString(-1L).hashCode();
        acc = acc * 31 + Long.bitCount(-1L);                    // 64
        acc = acc * 31 + Long.bitCount(0xdeadbeefcafebabeL);
        return acc;
    }

    public static int parseErrors() {
        int acc = 0;
        try {
            Integer.parseInt("zz", 10);
            acc = acc * 10 + 0;
        } catch (NumberFormatException e) {
            acc = acc * 10 + 1;
        }
        try {
            Integer.parseInt("", 16);
            acc = acc * 10 + 0;
        } catch (NumberFormatException e) {
            acc = acc * 10 + 1;
        }
        try {
            Integer.parseInt("2", 2); // digit out of radix
            acc = acc * 10 + 0;
        } catch (NumberFormatException e) {
            acc = acc * 10 + 1;
        }
        return acc;
    }
}
