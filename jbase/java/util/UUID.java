package java.util;

public final class UUID implements Comparable<UUID> {

    private final long mostSigBits;
    private final long leastSigBits;

    public UUID(long mostSigBits, long leastSigBits) {
        this.mostSigBits = mostSigBits;
        this.leastSigBits = leastSigBits;
    }

    public long getMostSignificantBits() {
        return mostSigBits;
    }

    public long getLeastSignificantBits() {
        return leastSigBits;
    }

    public static UUID fromString(String name) {
        // Canonical form is exactly 36 chars with dashes fixing the
        // 8-4-4-4-12 group lengths at positions 8, 13, 18 and 23.
        if (name.length() != 36
                || name.charAt(8) != '-'
                || name.charAt(13) != '-'
                || name.charAt(18) != '-'
                || name.charAt(23) != '-') {
            throw new IllegalArgumentException("Invalid UUID string: " + name);
        }
        long timeLow = parseHex(name, 0, 8);
        long timeMid = parseHex(name, 9, 13);
        long timeHi = parseHex(name, 14, 18);
        long clockSeq = parseHex(name, 19, 23);
        long node = parseHex(name, 24, 36);

        long msb = (timeLow << 32) | (timeMid << 16) | timeHi;
        long lsb = (clockSeq << 48) | node;
        return new UUID(msb, lsb);
    }

    // Parse hex digits in [start, end) into a long (unsigned accumulate).
    private static long parseHex(String s, int start, int end) {
        if (start >= end) {
            throw new IllegalArgumentException("Invalid UUID string: " + s);
        }
        long result = 0;
        for (int i = start; i < end; i++) {
            int digit = hexValue(s.charAt(i), s);
            result = (result << 4) | digit;
        }
        return result;
    }

    private static int hexValue(char c, String s) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        throw new IllegalArgumentException("Invalid UUID string: " + s);
    }

    public int version() {
        return (int) ((mostSigBits >> 12) & 0x0f);
    }

    public int variant() {
        // This field is composed of a varying number of bits.
        return (int) ((leastSigBits >>> (64 - (leastSigBits >>> 62)))
                & (leastSigBits >> 63));
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(36);
        digits(sb, mostSigBits >> 32, 8);
        sb.append('-');
        digits(sb, mostSigBits >> 16, 4);
        sb.append('-');
        digits(sb, mostSigBits, 4);
        sb.append('-');
        digits(sb, leastSigBits >> 48, 4);
        sb.append('-');
        digits(sb, leastSigBits, 12);
        return sb.toString();
    }

    // Append the low `count` hex digits of `val`, zero-padded, lowercase.
    private static void digits(StringBuilder sb, long val, int count) {
        for (int shift = (count - 1) * 4; shift >= 0; shift -= 4) {
            int nibble = (int) ((val >> shift) & 0x0f);
            sb.append(HEX[nibble]);
        }
    }

    private static final char[] HEX = {
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };

    public int hashCode() {
        long hilo = mostSigBits ^ leastSigBits;
        return (int) (hilo ^ (hilo >>> 32));
    }

    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof UUID)) {
            return false;
        }
        UUID id = (UUID) obj;
        return mostSigBits == id.mostSigBits
                && leastSigBits == id.leastSigBits;
    }

    public int compareTo(UUID val) {
        if (this.mostSigBits < val.mostSigBits) {
            return -1;
        }
        if (this.mostSigBits > val.mostSigBits) {
            return 1;
        }
        if (this.leastSigBits < val.leastSigBits) {
            return -1;
        }
        if (this.leastSigBits > val.leastSigBits) {
            return 1;
        }
        return 0;
    }
}
