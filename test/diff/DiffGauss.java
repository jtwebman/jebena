import java.util.Random;

public class DiffGauss {

    public static int single42() {
        Random r = new Random(42);
        double x = r.nextGaussian();
        return (int) (x * 1000000);
    }

    public static int single42Coarse() {
        Random r = new Random(42);
        double x = r.nextGaussian();
        return (int) (x * 1000);
    }

    public static int sum3Seed7() {
        Random r = new Random(7);
        double sum = r.nextGaussian() + r.nextGaussian() + r.nextGaussian();
        return (int) (sum * 1000000);
    }

    public static int sum3Seed7Coarse() {
        Random r = new Random(7);
        double sum = r.nextGaussian() + r.nextGaussian() + r.nextGaussian();
        return (int) (sum * 1000);
    }

    public static int combineSeed1() {
        Random r = new Random(1);
        double a = r.nextGaussian();
        double b = r.nextGaussian();
        return (int) ((a - b) * 1000000);
    }

    public static int combineSeed1Coarse() {
        Random r = new Random(1);
        double a = r.nextGaussian();
        double b = r.nextGaussian();
        return (int) ((a - b) * 1000);
    }

    public static int cachedPairSecond() {
        // First call fills the cache; second returns cached value.
        Random r = new Random(123);
        r.nextGaussian();
        double second = r.nextGaussian();
        return (int) (second * 1000000);
    }

    public static int reseedClearsCache() {
        // Draw one (fills cache), reseed (must clear), then draw fresh.
        Random r = new Random(99);
        r.nextGaussian();
        r.setSeed(99);
        double x = r.nextGaussian();
        return (int) (x * 1000000);
    }

    public static int bitsSeed42() {
        Random r = new Random(42);
        double x = r.nextGaussian();
        return (int) Double.doubleToLongBits(x);
    }
}
