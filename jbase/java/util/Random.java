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
}
