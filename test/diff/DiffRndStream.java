import java.util.Random;

public class DiffRndStream {

    // new Random(42).ints(5) -> plain 32-bit values, int-wrap sum
    public static int ints5sum() {
        return new Random(42).ints(5).sum();
    }

    // bounded ints in [0,100), summed
    public static int ints8bounded() {
        return new Random(7).ints(8, 0, 100).sum();
    }

    // bounded ints with a negative origin in [-50,50), summed
    public static int intsNegRange() {
        return new Random(13).ints(6, -50, 50).sum();
    }

    // empty stream sums to zero
    public static int intsZero() {
        return new Random(1).ints(0).sum();
    }

    // longs(4).count() == 4
    public static int longs4count() {
        return (int) new Random(3).longs(4).count();
    }

    // fold of longs via toArray, cast to int (identical arithmetic both sides)
    public static int longs4fold() {
        long[] a = new Random(3).longs(4).toArray();
        long acc = 0L;
        for (int i = 0; i < a.length; i++) {
            acc += a[i];
        }
        return (int) acc;
    }

    // sum of five longs, cast to int
    public static int longsSum() {
        long[] a = new Random(11).longs(5).toArray();
        long acc = 0L;
        for (int i = 0; i < a.length; i++) {
            acc += a[i];
        }
        return (int) acc;
    }

    // doubles(5): sum via toArray, scale and cast
    public static int doubles5() {
        double[] a = new Random(9).doubles(5).toArray();
        double s = 0.0;
        for (int i = 0; i < a.length; i++) {
            s += a[i];
        }
        return (int) (s * 1000000.0);
    }

    // doubles(3) with a different seed
    public static int doubles3() {
        double[] a = new Random(21).doubles(3).toArray();
        double s = 0.0;
        for (int i = 0; i < a.length; i++) {
            s += a[i];
        }
        return (int) (s * 1000000.0);
    }
}
