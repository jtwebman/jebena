package java.util;

import java.lang.IllegalArgumentException;

/**
 * Clean-room java.util.SplittableRandom for Jebena. Implements the SplitMix64
 * algorithm specified by the platform: the generator advances a 64-bit seed by
 * a fixed golden-ratio gamma increment and passes the pre-increment value
 * through the SplitMix64 mixing functions. For a fixed seed the produced stream
 * matches the specification bit-for-bit. Pure bytecode: no native methods.
 */
public class SplittableRandom {

    // Golden-ratio odd constant used as the step (gamma) for the root instance.
    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private static final double DOUBLE_UNIT = 0x1.0p-53;

    private long seed;

    public SplittableRandom(long seed) {
        this.seed = seed;
    }

    private synchronized long nextSeed() {
        return (seed += GOLDEN_GAMMA);
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static int mix32(long z) {
        z = (z ^ (z >>> 33)) * 0x62A9D9ED799705F5L;
        return (int) (((z ^ (z >>> 28)) * 0xCB24D0A5C88C35B3L) >>> 32);
    }

    public long nextLong() {
        return mix64(nextSeed());
    }

    public int nextInt() {
        return mix32(nextSeed());
    }

    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        int r = mix32(nextSeed());
        int m = bound - 1;
        if ((bound & m) == 0) {
            // Power of two: low bits are uniform.
            r &= m;
        } else {
            // Rejection sampling to remove modulo bias.
            for (int u = r >>> 1; u + m - (r = u % bound) < 0; u = mix32(nextSeed()) >>> 1) {
                // retry
            }
        }
        return r;
    }

    public double nextDouble() {
        return (nextLong() >>> 11) * DOUBLE_UNIT;
    }
}
