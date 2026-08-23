import java.util.SplittableRandom;

/**
 * Differential coverage for java.util.SplittableRandom (SplitMix64) at a fixed
 * seed. Encoding is int-as-described: longs folded to (int), doubles scaled.
 */
public class DiffSplitRnd {

    static int firstInt() {
        return new SplittableRandom(42).nextInt();
    }

    static int sumThreeLongs() {
        SplittableRandom r = new SplittableRandom(42);
        long s = r.nextLong() + r.nextLong() + r.nextLong();
        return (int) s;
    }

    static int boundedFold() {
        SplittableRandom r = new SplittableRandom(42);
        int acc = 0;
        for (int i = 0; i < 8; i++) {
            acc = acc * 31 + r.nextInt(100);
        }
        return acc;
    }

    static int doubleScaled() {
        SplittableRandom r = new SplittableRandom(42);
        return (int) (r.nextDouble() * 1e9);
    }

    static int boundedPow2() {
        SplittableRandom r = new SplittableRandom(7);
        int acc = 0;
        for (int i = 0; i < 8; i++) {
            acc = acc * 31 + r.nextInt(64);
        }
        return acc;
    }

    static int sameSeedEqual() {
        SplittableRandom a = new SplittableRandom(12345);
        SplittableRandom b = new SplittableRandom(12345);
        return a.nextInt() == b.nextInt() ? 1 : 0;
    }
}
