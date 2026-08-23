public class DiffUnsigned {
    private static int checksum(String s) {
        int h = 0;
        for (int i = 0; i < s.length(); i++) {
            h = h * 31 + s.charAt(i);
        }
        return h;
    }

    public static int parseIntRadix() {
        return Integer.parseInt("ff", 16);
    }

    public static int parseUnsigned() {
        return Integer.parseUnsignedInt("4294967295");
    }

    public static int toUnsignedLongLow() {
        return (int) (Integer.toUnsignedLong(-1) - 1L);
    }

    public static int divideUnsignedInt() {
        return Integer.divideUnsigned(-1, 2);
    }

    public static int remainderUnsignedInt() {
        return Integer.remainderUnsigned(-3, 10);
    }

    public static int compareUnsignedInt() {
        return Integer.compareUnsigned(-1, 1);
    }

    public static int rotateLeftInt() {
        return Integer.rotateLeft(1, 4);
    }

    public static int rotateRightInt() {
        return Integer.rotateRight(16, 4);
    }

    public static int toUnsignedStringInt() {
        return checksum(Integer.toUnsignedString(-1));
    }

    public static int parseLongRadix() {
        return (int) Long.parseLong("zz", 36);
    }

    public static int divideUnsignedLong() {
        return (int) Long.divideUnsigned(-1L, 2L);
    }

    public static int remainderUnsignedLong() {
        return (int) Long.remainderUnsigned(-1L, 7L);
    }

    public static int compareUnsignedLong() {
        return Long.compareUnsigned(-1L, 1L);
    }

    public static int rotateLeftLong() {
        return (int) Long.rotateLeft(1L, 8);
    }

    public static int toUnsignedStringLong() {
        return checksum(Long.toUnsignedString(-1L));
    }
}
