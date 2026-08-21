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
}
