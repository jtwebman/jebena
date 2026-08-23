import java.util.stream.IntStream;

public class DiffIntStream2 {

    public static int mapToObjCount() {
        return (int) IntStream.rangeClosed(1, 5).mapToObj(x -> "n" + x).count();
    }

    public static int asLongSum() {
        return (int) IntStream.range(1, 4).asLongStream().sum();
    }

    public static int mapToDoubleSum() {
        double s = IntStream.of(1, 2, 3).mapToDouble(x -> x + 0.5).sum();
        return (int) (s * 100);
    }

    public static int flatMapSum() {
        return IntStream.of(1, 2, 3).flatMap(x -> IntStream.range(0, x)).sum();
    }

    public static int asDoubleAvg() {
        double a = IntStream.of(1, 2, 3, 4).asDoubleStream().average().getAsDouble();
        return (int) (a * 100);
    }

    public static int mapToLongSum() {
        return (int) IntStream.of(2, 4, 6).mapToLong(x -> (long) x * 10).sum();
    }

    public static int flatMapCount() {
        return (int) IntStream.rangeClosed(1, 4).flatMap(x -> IntStream.rangeClosed(1, x)).count();
    }

    public static int mapToDoubleDistinctCount() {
        return (int) IntStream.of(1, 1, 2, 3, 3).distinct().mapToDouble(x -> x * 1.5).count();
    }
}
