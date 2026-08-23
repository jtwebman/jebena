import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.stream.LongStream;

public class DiffLongStream {

    public static int rangeClosedSum() {
        return (int) LongStream.rangeClosed(1, 10).sum();
    }

    public static int rangeMapSquareSum() {
        return (int) LongStream.range(1, 6).map(x -> x * x).sum();
    }

    public static int ofMax() {
        return (int) LongStream.of(3, 1, 2).max().getAsLong();
    }

    public static int ofMin() {
        return (int) LongStream.of(3, 1, 2).min().getAsLong();
    }

    public static int filterEvenSum() {
        return (int) LongStream.rangeClosed(1, 10).filter(x -> x % 2 == 0).sum();
    }

    public static int reduceProduct() {
        return (int) LongStream.rangeClosed(1, 5).reduce(1L, (a, b) -> a * b);
    }

    public static int averageMicros() {
        OptionalDouble avg = LongStream.rangeClosed(1, 4).average();
        return (int) (avg.getAsDouble() * 1000000);
    }

    public static int boxedCount() {
        return (int) LongStream.range(0, 7).boxed().count();
    }

    public static int emptyMinPresent() {
        OptionalLong m = LongStream.range(5, 5).min();
        return m.isPresent() ? 1 : 0;
    }

    public static int toArrayChecksum() {
        long[] a = LongStream.of(10, -3, 7, 100).toArray();
        int acc = 0;
        for (int i = 0; i < a.length; i++) {
            acc = acc * 31 + (int) a[i];
        }
        return acc;
    }
}
