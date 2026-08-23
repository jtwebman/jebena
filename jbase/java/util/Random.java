package java.util;

import java.lang.IllegalArgumentException;

/**
 * Clean-room java.util.Random for Jebena. Implements the exact 48-bit linear
 * congruential generator specified by java.util.Random, so that for any given
 * seed the produced stream matches the spec byte-for-byte. Pure bytecode: there
 * are no native methods. The seeded constructor and setSeed reproduce the spec
 * exactly; the no-arg constructor uses a deterministic static counter rather
 * than a clock, since only seeded instances are required to match.
 */
public class Random {

    private long seed;

    // Cached second Gaussian value produced by the Marsaglia polar method.
    private double nextNextGaussian;
    private boolean haveNextNextGaussian = false;

    private static final long MULTIPLIER = 0x5DEECE66DL;
    private static final long ADDEND = 0xBL;
    private static final long MASK = (1L << 48) - 1;

    // Deterministic seed source for the no-arg constructor. Starts at a fixed
    // constant and is scrambled by a multiplicative step on each construction.
    private static long seedUniquifier = 8682522807148012L;

    private static long nextSeedUniquifier() {
        seedUniquifier *= 1181783497276652981L;
        return seedUniquifier;
    }

    public Random() {
        setSeed(nextSeedUniquifier());
    }

    public Random(long seed) {
        setSeed(seed);
    }

    public void setSeed(long seed) {
        this.seed = (seed ^ MULTIPLIER) & MASK;
        this.haveNextNextGaussian = false;
    }

    protected int next(int bits) {
        seed = (seed * MULTIPLIER + ADDEND) & MASK;
        return (int) (seed >>> (48 - bits));
    }

    public int nextInt() {
        return next(32);
    }

    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        int r = next(31);
        int m = bound - 1;
        if ((bound & m) == 0) {
            r = (int) ((bound * (long) r) >> 31);
        } else {
            for (int u = r; u - (r = u % bound) + m < 0; u = next(31)) {
                // spin until we land in an unbiased range
            }
        }
        return r;
    }

    public long nextLong() {
        return ((long) (next(32)) << 32) + next(32);
    }

    public boolean nextBoolean() {
        return next(1) != 0;
    }

    public float nextFloat() {
        return next(24) / ((float) (1 << 24));
    }

    public double nextDouble() {
        return (((long) (next(26)) << 27) + next(27)) * 0x1.0p-53;
    }

    /**
     * Returns a Gaussian ("normally") distributed double with mean 0.0 and
     * standard deviation 1.0, using the Marsaglia polar method exactly as
     * specified by java.util.Random. A second value produced by each polar
     * pair is cached and returned on the following call. Bit-exactness with
     * the reference JDK depends on Math.sqrt/Math.log matching StrictMath.
     */
    public double nextGaussian() {
        if (haveNextNextGaussian) {
            haveNextNextGaussian = false;
            return nextNextGaussian;
        } else {
            double v1, v2, s;
            do {
                v1 = 2 * nextDouble() - 1;
                v2 = 2 * nextDouble() - 1;
                s = v1 * v1 + v2 * v2;
            } while (s >= 1 || s == 0);
            double multiplier = Math.sqrt(-2 * Math.log(s) / s);
            nextNextGaussian = v2 * multiplier;
            haveNextNextGaussian = true;
            return v1 * multiplier;
        }
    }

    // Bounded int helper matching the specified stream behavior: for a
    // representable range it draws nextInt(bound - origin) and shifts by
    // origin; for a range too large to fit in an int it rejects samples
    // outside [origin, bound).
    private int internalNextInt(int origin, int bound) {
        int n = bound - origin;
        if (n > 0) {
            return nextInt(n) + origin;
        }
        int r;
        do {
            r = nextInt();
        } while (r < origin || r >= bound);
        return r;
    }

    public java.util.stream.IntStream ints(long streamSize) {
        if (streamSize < 0L) {
            throw new IllegalArgumentException("size must be non-negative");
        }
        int[] out = new int[(int) streamSize];
        for (int i = 0; i < out.length; i++) {
            out[i] = nextInt();
        }
        return java.util.stream.IntStream.of(out);
    }

    public java.util.stream.IntStream ints(long streamSize, int randomNumberOrigin, int randomNumberBound) {
        if (streamSize < 0L) {
            throw new IllegalArgumentException("size must be non-negative");
        }
        if (randomNumberOrigin >= randomNumberBound) {
            throw new IllegalArgumentException("bound must be greater than origin");
        }
        int[] out = new int[(int) streamSize];
        for (int i = 0; i < out.length; i++) {
            out[i] = internalNextInt(randomNumberOrigin, randomNumberBound);
        }
        return java.util.stream.IntStream.of(out);
    }

    public java.util.stream.LongStream longs(long streamSize) {
        if (streamSize < 0L) {
            throw new IllegalArgumentException("size must be non-negative");
        }
        long[] out = new long[(int) streamSize];
        for (int i = 0; i < out.length; i++) {
            out[i] = nextLong();
        }
        return java.util.stream.LongStream.of(out);
    }

    public java.util.stream.DoubleStream doubles(long streamSize) {
        if (streamSize < 0L) {
            throw new IllegalArgumentException("size must be non-negative");
        }
        double[] out = new double[(int) streamSize];
        for (int i = 0; i < out.length; i++) {
            out[i] = nextDouble();
        }
        return java.util.stream.DoubleStream.of(out);
    }
}
