import java.util.stream.Stream;

public class DiffStream3 {

    // Stream.iterate(1, x-><=100, x->*2): 1,2,4,8,16,32,64 -> count 7
    public static int iterateCount() {
        return (int) Stream.iterate(Integer.valueOf(1),
                x -> ((Integer) x).intValue() <= 100,
                x -> Integer.valueOf(((Integer) x).intValue() * 2)).count();
    }

    // sum of 1,2,4,8,16,32,64 = 127 via mapToInt
    public static int iterateSumInt() {
        return Stream.iterate(Integer.valueOf(1),
                x -> ((Integer) x).intValue() <= 100,
                x -> Integer.valueOf(((Integer) x).intValue() * 2))
                .mapToInt(x -> ((Integer) x).intValue()).sum();
    }

    // sum via reduce = 127
    public static int iterateSumReduce() {
        Object r = Stream.iterate(Integer.valueOf(1),
                x -> ((Integer) x).intValue() <= 100,
                x -> Integer.valueOf(((Integer) x).intValue() * 2))
                .reduce(Integer.valueOf(0),
                        (a, b) -> Integer.valueOf(((Integer) a).intValue() + ((Integer) b).intValue()));
        return ((Integer) r).intValue();
    }

    // iterate that never enters loop -> empty
    public static int iterateEmpty() {
        return (int) Stream.iterate(Integer.valueOf(5),
                x -> ((Integer) x).intValue() < 0,
                x -> Integer.valueOf(((Integer) x).intValue() - 1)).count();
    }

    // mapToLong sum over 1,2,3 = 6 -> (int)6
    public static int mapToLongSum() {
        long s = Stream.of(Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3))
                .mapToLong(x -> (long) ((Integer) x).intValue()).sum();
        return (int) s;
    }

    // mapToLong big values to exercise long width: (long)Integer.MAX * (idx) accumulation
    public static int mapToLongWide() {
        long s = Stream.of(Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3))
                .mapToLong(x -> ((Integer) x).intValue() * 1000000000L).sum();
        return (int) s; // 6000000000 -> truncated int
    }

    // mapToDouble sum: (1.5+2.5+3.5)=7.5 -> (int)(7.5*100)=750
    public static int mapToDoubleSum() {
        double s = Stream.of(Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3))
                .mapToDouble(x -> ((Integer) x).intValue() + 0.5).sum();
        return (int) (s * 100);
    }

    // mapToDouble over empty stream -> 0.0
    public static int mapToDoubleEmpty() {
        double s = Stream.of().mapToDouble(x -> ((Integer) x).intValue() + 0.5).sum();
        return (int) (s * 100);
    }

    // combined: iterate then mapToDouble sum. 1,2,4,8,16,32,64 each +0.5 -> 127 + 3.5 = 130.5
    public static int iterateMapToDouble() {
        double s = Stream.iterate(Integer.valueOf(1),
                x -> ((Integer) x).intValue() <= 100,
                x -> Integer.valueOf(((Integer) x).intValue() * 2))
                .mapToDouble(x -> ((Integer) x).intValue() + 0.5).sum();
        return (int) (s * 100); // 130.5*100 = 13050
    }
}
