public class DiffMath4 {
    public static int mhMax() {
        return (int) Math.multiplyHigh(0x7fffffffffffffffL, 4L);
    }

    public static int mhShift() {
        return (int) Math.multiplyHigh(1L << 40, 1L << 40);
    }

    public static int mhNeg() {
        return (int) Math.multiplyHigh(-3L, 1L << 62);
    }

    public static int mhBothNeg() {
        return (int) Math.multiplyHigh(-1L << 50, -1L << 50);
    }

    public static int mhZero() {
        return (int) Math.multiplyHigh(0L, 0x1234567890abcdefL);
    }

    public static int mhMinMin() {
        return (int) Math.multiplyHigh(Long.MIN_VALUE, Long.MIN_VALUE);
    }

    public static int mhMixed() {
        return (int) Math.multiplyHigh(6148914691236517205L, -3L);
    }

    public static int mhOne() {
        return (int) Math.multiplyHigh(-1L, 0x7fffffffffffffffL);
    }
}
