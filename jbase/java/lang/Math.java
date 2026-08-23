package java.lang;

/**
 * Clean-room java.lang.Math for Jebena (SE25 spec). Integer/long abs/max/min are
 * pure bytecode; the transcendental and floating-point methods (sqrt, floor, sin,
 * ...) are native in the spec and will dispatch to the Zig native registry later.
 * Note abs(MIN_VALUE) overflows back to MIN_VALUE, matching the spec.
 */
public final class Math {
    private Math() {}

    public static int abs(int a) {
        return (a < 0) ? -a : a;
    }

    public static long abs(long a) {
        return (a < 0L) ? -a : a;
    }

    public static int max(int a, int b) {
        return (a >= b) ? a : b;
    }

    public static long max(long a, long b) {
        return (a >= b) ? a : b;
    }

    public static int min(int a, int b) {
        return (a <= b) ? a : b;
    }

    public static long min(long a, long b) {
        return (a <= b) ? a : b;
    }

    public static int floorDiv(int x, int y) {
        int r = x / y;
        // if signs differ and rounding was toward zero (not down), adjust.
        if ((x ^ y) < 0 && (r * y != x)) r--;
        return r;
    }

    public static int floorMod(int x, int y) {
        return x - floorDiv(x, y) * y;
    }

    // VM-provided (native in the spec): floating-point math dispatched to Zig.
    public static native double sqrt(double a);
    public static native double cbrt(double a);
    public static native double floor(double a);
    public static native double ceil(double a);
    public static native double abs(double a);
    public static native double pow(double a, double b);
    public static native double exp(double a);
    public static native double log(double a);
    public static native double log10(double a);
    public static native double hypot(double x, double y);
    public static native long round(double a);
    public static native int round(float a);

    // Pure integer helpers (overflow-checked exact ops throw ArithmeticException).
    public static int toIntExact(long value) {
        if ((int) value != value) {
            throw new ArithmeticException("integer overflow");
        }
        return (int) value;
    }

    public static int addExact(int x, int y) {
        int r = x + y;
        if (((x ^ r) & (y ^ r)) < 0) {
            throw new ArithmeticException("integer overflow");
        }
        return r;
    }

    public static long addExact(long x, long y) {
        long r = x + y;
        if (((x ^ r) & (y ^ r)) < 0) {
            throw new ArithmeticException("long overflow");
        }
        return r;
    }

    public static int subtractExact(int x, int y) {
        int r = x - y;
        if (((x ^ y) & (x ^ r)) < 0) {
            throw new ArithmeticException("integer overflow");
        }
        return r;
    }

    public static int multiplyExact(int x, int y) {
        long r = (long) x * (long) y;
        if ((int) r != r) {
            throw new ArithmeticException("integer overflow");
        }
        return (int) r;
    }

    public static long floorDiv(long x, long y) {
        long q = x / y;
        if ((x ^ y) < 0 && q * y != x) {
            q--;
        }
        return q;
    }

    public static long floorMod(long x, long y) {
        return x - floorDiv(x, y) * y;
    }
}
