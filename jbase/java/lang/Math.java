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

    public static long subtractExact(long x, long y) {
        long r = x - y;
        if (((x ^ y) & (x ^ r)) < 0L) {
            throw new ArithmeticException("long overflow");
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

    public static long multiplyExact(long x, long y) {
        long r = x * y;
        long ax = abs(x);
        long ay = abs(y);
        if (((ax | ay) >>> 31 != 0)) {
            // Some bits greater than 2^31 that might cause overflow; check.
            if (((y != 0) && (r / y != x)) ||
                (x == Long.MIN_VALUE && y == -1)) {
                throw new ArithmeticException("long overflow");
            }
        }
        return r;
    }

    public static int negateExact(int a) {
        if (a == Integer.MIN_VALUE) {
            throw new ArithmeticException("integer overflow");
        }
        return -a;
    }

    public static long negateExact(long a) {
        if (a == Long.MIN_VALUE) {
            throw new ArithmeticException("long overflow");
        }
        return -a;
    }

    public static int incrementExact(int a) {
        if (a == Integer.MAX_VALUE) {
            throw new ArithmeticException("integer overflow");
        }
        return a + 1;
    }

    public static long incrementExact(long a) {
        if (a == Long.MAX_VALUE) {
            throw new ArithmeticException("long overflow");
        }
        return a + 1L;
    }

    public static int decrementExact(int a) {
        if (a == Integer.MIN_VALUE) {
            throw new ArithmeticException("integer overflow");
        }
        return a - 1;
    }

    public static long decrementExact(long a) {
        if (a == Long.MIN_VALUE) {
            throw new ArithmeticException("long overflow");
        }
        return a - 1L;
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

    // High 64 bits of the 128-bit signed product x*y, via 32-bit limbs.
    public static long multiplyHigh(long x, long y) {
        long x1 = x >> 32;
        long x2 = x & 0xFFFFFFFFL;
        long y1 = y >> 32;
        long y2 = y & 0xFFFFFFFFL;
        long z2 = x2 * y2;
        long t = x1 * y2 + (z2 >>> 32);
        long z1 = t & 0xFFFFFFFFL;
        long z0 = t >> 32;
        z1 += x2 * y1;
        return x1 * y1 + z0 + (z1 >> 32);
    }

    // floorDiv variants that throw on the single overflowing quotient (MIN/-1).
    public static int floorDivExact(int x, int y) {
        int q = x / y;
        if ((x & y & q) >= 0) {
            if ((x ^ y) < 0 && q * y != x) {
                return q - 1;
            }
            return q;
        }
        throw new ArithmeticException("integer overflow");
    }

    public static long floorDivExact(long x, long y) {
        long q = x / y;
        if ((x & y & q) >= 0L) {
            if ((x ^ y) < 0L && q * y != x) {
                return q - 1L;
            }
            return q;
        }
        throw new ArithmeticException("long overflow");
    }

    // Division rounding the quotient toward positive infinity (ceiling).
    public static int ceilDiv(int x, int y) {
        int q = x / y;
        if ((x ^ y) >= 0 && q * y != x) {
            return q + 1;
        }
        return q;
    }

    public static long ceilDiv(long x, long y) {
        long q = x / y;
        if ((x ^ y) >= 0L && q * y != x) {
            return q + 1L;
        }
        return q;
    }

    public static int ceilMod(int x, int y) {
        return x - ceilDiv(x, y) * y;
    }

    public static long ceilMod(long x, long y) {
        return x - ceilDiv(x, y) * y;
    }

    // abs that throws instead of returning MIN_VALUE unchanged.
    public static int absExact(int a) {
        if (a == Integer.MIN_VALUE) {
            throw new ArithmeticException(
                "Overflow to represent absolute value of Integer.MIN_VALUE");
        }
        return (a < 0) ? -a : a;
    }

    public static long absExact(long a) {
        if (a == Long.MIN_VALUE) {
            throw new ArithmeticException(
                "Overflow to represent absolute value of Long.MIN_VALUE");
        }
        return (a < 0L) ? -a : a;
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

    public static double rint(double a) {
        // Round to nearest, ties to even (round-half-to-even), returning a
        // double. The "add/subtract 2^52" trick forces the FPU (which uses
        // round-to-nearest-even) to drop the fractional bits for |a| < 2^52;
        // values with magnitude >= 2^52 are already integral.
        final double twoToThe52 = 4.503599627370496E15; // 2^52
        double sign = copySign(1.0, a); // preserve sign of -0.0
        double mag = abs(a);
        if (mag < twoToThe52) {
            mag = (twoToThe52 + mag) - twoToThe52;
        }
        return sign * mag;
    }

    public static int getExponent(double d) {
        long bits = Double.doubleToRawLongBits(d);
        // Extract the biased exponent and remove the 1023 bias. For subnormals
        // and zero this yields MIN_EXPONENT-1 (-1023); for NaN/Infinity it
        // yields MAX_EXPONENT+1 (1024), matching the spec.
        return (int) (((bits >> 52) & 0x7ffL) - 1023L);
    }

    public static double scalb(double d, int scaleFactor) {
        // Returns d * 2^scaleFactor with a single correct rounding. The scale is
        // applied in chunks of at most 512 (each an exactly representable normal
        // power of two, since 512 <= MAX_EXPONENT and -512 >= MIN_EXPONENT) so
        // that under/overflow, if any, happens monotonically in the last steps.
        final int MAX_SCALE = 1023 + 1022 + 53 + 1; // 2099: guarantees over/underflow
        int scaleIncrement;
        double expDelta;
        if (scaleFactor < 0) {
            scaleFactor = max(scaleFactor, -MAX_SCALE);
            scaleIncrement = -512;
            expDelta = Double.longBitsToDouble(0x1ff0000000000000L); // 2^-512
        } else {
            scaleFactor = min(scaleFactor, MAX_SCALE);
            scaleIncrement = 512;
            expDelta = Double.longBitsToDouble(0x5ff0000000000000L); // 2^512
        }
        // Remainder of scaleFactor modulo 512, same sign as scaleFactor, so that
        // (scaleFactor - expAdjust) is a multiple of 512.
        int expAdjust;
        if (scaleFactor >= 0) {
            expAdjust = scaleFactor & 511;
        } else {
            expAdjust = -((-scaleFactor) & 511);
        }
        d *= powerOfTwoD(expAdjust);
        scaleFactor -= expAdjust;
        while (scaleFactor != 0) {
            d *= expDelta;
            scaleFactor -= scaleIncrement;
        }
        return d;
    }

    // 2^n as a normal double; valid only for MIN_EXPONENT <= n <= MAX_EXPONENT.
    private static double powerOfTwoD(int n) {
        return Double.longBitsToDouble(((long) (n + 1023)) << 52);
    }

    public static double nextAfter(double start, double direction) {
        if (start != start || direction != direction) {
            return start + direction; // NaN
        }
        if (start == direction) {
            return direction;
        }
        // Adding +0.0 collapses -0.0 to +0.0 so the zero case has bits == 0.
        long transducer = Double.doubleToRawLongBits(start + 0.0d);
        if (direction > start) { // ascending toward +infinity
            transducer = transducer + (transducer >= 0L ? 1L : -1L);
        } else { // descending toward -infinity (direction < start)
            if (transducer > 0L) {
                transducer = transducer - 1L;
            } else if (transducer < 0L) {
                transducer = transducer + 1L;
            } else {
                transducer = 0x8000000000000001L; // -Double.MIN_VALUE
            }
        }
        return Double.longBitsToDouble(transducer);
    }
}
