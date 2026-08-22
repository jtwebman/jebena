import java.util.StringJoiner;

/**
 * Differential coverage for bit-twiddling + text-building breadth: Integer/Long
 * bit ops (bitCount / numberOfLeading|TrailingZeros / highest|lowestOneBit /
 * reverse / toHexString / toBinaryString), StringBuilder delete/deleteCharAt/
 * setCharAt/reverse/insert, and java.util.StringJoiner. Every method returns a
 * deterministic int checked byte-for-byte vs real java.
 */
public class DiffBits {
    static int intBitCount() {
        return Integer.bitCount(0) * 10000
                + Integer.bitCount(255) * 100
                + Integer.bitCount(-1); // 0, 8, 32 -> 0*10000 + 8*100 + 32 = 832
    }

    static int intNlz() {
        return Integer.numberOfLeadingZeros(1) * 1000 // 31
                + Integer.numberOfLeadingZeros(1024) * 10 // 21
                + Integer.numberOfLeadingZeros(0); // 32 -> 31000 + 210 + 32 = 31242
    }

    static int intNtz() {
        return Integer.numberOfTrailingZeros(1) * 1000
                + Integer.numberOfTrailingZeros(8) * 100
                + Integer.numberOfTrailingZeros(0x10000) // 16
                + Integer.numberOfTrailingZeros(0); // 32
        // 0*1000 + 3*100 + 16 + 32 = 348
    }

    static int intHighLow() {
        return Integer.highestOneBit(100) * 10 // 64 -> 640
                + (Integer.lowestOneBit(12) & 0xf); // 4
    }

    static int intReverse() {
        // reverse(1) = 0x80000000 (Integer.MIN_VALUE); compare via >>> to keep it positive
        return (Integer.reverse(1) >>> 28) & 0xf; // top nibble of 0x8000_0000 -> 8
    }

    static int hexBin() {
        // "ff".length()=2, "1010".length()=4, encode
        return Integer.toHexString(255).length() * 100
                + Integer.toBinaryString(10).length() * 10
                + Integer.toHexString(-1).length(); // "ffffffff" -> 8 ; 2*100+4*10+8 = 248
    }

    static int longBits() {
        return Long.bitCount(-1L) * 100 // 64
                + Long.toHexString(255L).length() * 10 // "ff" -> 2
                + Long.toBinaryString(8L).length(); // "1000" -> 4 ; 6400 + 20 + 4 = 6424
    }

    static int longHexValue() {
        // sum the chars of Long.toHexString of a known value
        String h = Long.toHexString(0xABCDEF12L); // "abcdef12"
        int acc = 0;
        for (int i = 0; i < h.length(); i++) {
            acc += h.charAt(i);
        }
        return acc;
    }

    static int sbDelete() {
        StringBuilder sb = new StringBuilder("hello world");
        sb.delete(5, 11); // "hello"
        sb.setCharAt(0, 'H'); // "Hello"
        sb.deleteCharAt(4); // "Hell"
        return sb.length() * 100 + sb.charAt(0); // 4*100 + 'H'(72) = 472
    }

    static int sbInsertReverse() {
        StringBuilder sb = new StringBuilder("abc");
        sb.insert(1, "XY"); // "aXYbc"
        sb.reverse(); // "cbYXa"
        int acc = 0;
        for (int i = 0; i < sb.length(); i++) {
            acc = acc * 31 + sb.charAt(i);
        }
        return acc;
    }

    static int joiner() {
        StringJoiner sj = new StringJoiner(", ", "[", "]");
        sj.add("a").add("b").add("c");
        String s = sj.toString(); // "[a, b, c]"
        return s.length(); // 9
    }

    static int joinerEmpty() {
        StringJoiner sj = new StringJoiner(",");
        sj.setEmptyValue("EMPTY");
        return sj.toString().length(); // "EMPTY" -> 5
    }
}
