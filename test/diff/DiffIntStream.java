import java.util.stream.IntStream;

public class DiffIntStream {

    public static int filterSum() {
        return IntStream.rangeClosed(1, 10).filter(x -> x % 2 == 0).sum();
    }

    public static int reduceSum() {
        return IntStream.range(1, 6).reduce(0, (a, b) -> a + b);
    }

    public static int minMax() {
        int lo = IntStream.of(3, 1, 2).min().getAsInt();
        int hi = IntStream.of(3, 1, 2).max().getAsInt();
        return lo * 100 + hi;
    }

    public static int average() {
        double avg = IntStream.rangeClosed(1, 4).average().getAsDouble();
        return (int) (avg * 1000000);
    }

    public static int distinctSum() {
        return IntStream.of(3, 1, 2, 3, 1).distinct().sum();
    }

    public static int sortedChecksum() {
        int[] a = IntStream.of(5, 3, 1, 4).sorted().toArray();
        int acc = 0;
        for (int i = 0; i < a.length; i++) {
            acc = acc * 31 + a[i];
        }
        return acc;
    }

    public static int limitSum() {
        return IntStream.range(0, 100).limit(5).sum();
    }

    public static int skipSum() {
        return IntStream.range(0, 10).skip(7).sum();
    }

    public static int mapToObjCount() {
        return (int) IntStream.rangeClosed(1, 3).mapToObj(x -> "n" + x).count();
    }
}
