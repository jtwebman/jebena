package java.lang;

/**
 * Clean-room java.lang.Math for Jebena (SE25 spec). Integer/long abs/max/min are
 * pure bytecode; the transcendental and floating-point methods (sqrt, floor, sin,
 * ...) are native in the spec and will dispatch to the Zig native registry later.
 * Note abs(MIN_VALUE) overflows back to MIN_VALUE, matching the spec.
 */
public final class Math {
    private Math() {}

    public static final double PI = 3.141592653589793;
    public static final double E = 2.718281828459045;

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

    // ---- Pure floating-point helpers (bit-exact, no libm needed) ----

    public static double toRadians(double angdeg) {
        return angdeg / 180.0 * PI;
    }

    public static double toDegrees(double angrad) {
        return angrad * 180.0 / PI;
    }

    public static double copySign(double magnitude, double sign) {
        long m = Double.doubleToRawLongBits(magnitude);
        long s = Double.doubleToRawLongBits(sign);
        return Double.longBitsToDouble((m & 0x7fffffffffffffffL) | (s & 0x8000000000000000L));
    }

    public static float copySign(float magnitude, float sign) {
        int m = Float.floatToRawIntBits(magnitude);
        int s = Float.floatToRawIntBits(sign);
        return Float.intBitsToFloat((m & 0x7fffffff) | (s & 0x80000000));
    }

    public static double signum(double d) {
        // NaN and +/-0.0 return the argument unchanged.
        if (d != d || d == 0.0) {
            return d;
        }
        return copySign(1.0, d);
    }

    public static float signum(float f) {
        if (f != f || f == 0.0f) {
            return f;
        }
        return copySign(1.0f, f);
    }

    public static double ulp(double d) {
        long bits = Double.doubleToRawLongBits(d);
        long biasedExp = (bits >> 52) & 0x7ffL;
        if (biasedExp == 0x7ffL) {
            // NaN or infinity -> abs(d)
            return Double.longBitsToDouble(bits & 0x7fffffffffffffffL);
        }
        if (biasedExp == 0L) {
            // zero or subnormal -> smallest positive double (2^-1074)
            return Double.longBitsToDouble(1L);
        }
        // normal: unbiased exponent e, ulp exponent = e - 52
        long ulpExp = (biasedExp - 1023L) - 52L;
        if (ulpExp >= -1022L) {
            // representable as a normal number
            return Double.longBitsToDouble((ulpExp + 1023L) << 52);
        }
        // ulp itself is subnormal: 2^ulpExp = longBitsToDouble(1L << (ulpExp + 1074))
        return Double.longBitsToDouble(1L << (int) (ulpExp + 1074L));
    }

    public static double nextUp(double d) {
        if (d != d) {
            return d; // NaN
        }
        long bits = Double.doubleToRawLongBits(d);
        if (bits == 0x7ff0000000000000L) {
            return d; // positive infinity
        }
        d = d + 0.0; // collapse -0.0 to +0.0
        bits = Double.doubleToRawLongBits(d);
        if (d >= 0.0) {
            return Double.longBitsToDouble(bits + 1L);
        }
        return Double.longBitsToDouble(bits - 1L);
    }

    public static double nextDown(double d) {
        if (d != d) {
            return d; // NaN
        }
        long bits = Double.doubleToRawLongBits(d);
        if (bits == 0xfff0000000000000L) {
            return d; // negative infinity
        }
        if (d == 0.0) {
            return Double.longBitsToDouble(0x8000000000000001L); // -MIN_VALUE
        }
        if (d > 0.0) {
            return Double.longBitsToDouble(bits - 1L);
        }
        return Double.longBitsToDouble(bits + 1L);
    }
}
