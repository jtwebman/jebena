import java.util.stream.LongStream;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

public class DiffLDStream {

    public static int mapToObjCount() {
        Stream s = LongStream.rangeClosed(1, 5).mapToObj(x -> "n" + x);
        return (int) s.count();
    }

    public static int sortedLimitSum() {
        return (int) LongStream.of(3, 1, 2).sorted().limit(2).sum();
    }

    public static int distinctSum() {
        return (int) LongStream.of(1, 1, 2, 3, 3).distinct().sum();
    }

    public static int doubleDistinctCount() {
        return (int) DoubleStream.of(1.5, 2.5, 1.5).distinct().count();
    }

    public static int doubleSortedSkipSum() {
        return (int) (DoubleStream.of(3.0, 1.0, 2.0).sorted().skip(1).sum() * 10);
    }

    public static int asDoubleStreamSum() {
        return (int) DoubleStream.of(LongStream.of(5, 10).asDoubleStream().toArray()).sum();
    }

    public static int mapToIntSum() {
        return LongStream.of(10, 20, 30).mapToInt(x -> (int) (x / 10)).sum();
    }

    public static int doubleSkipLimit() {
        return (int) DoubleStream.of(1.0, 2.0, 3.0, 4.0, 5.0).skip(1).limit(2).sum();
    }

    public static int doubleMapToLongSum() {
        return (int) DoubleStream.of(1.9, 2.9, 3.9).mapToLong(x -> (long) x).sum();
    }

    public static int longSkipSum() {
        return (int) LongStream.rangeClosed(1, 5).skip(2).sum();
    }
}
