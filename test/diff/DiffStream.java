import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Differential coverage for java.util.stream: the Stream pipeline (filter/map/
 * distinct/sorted/limit/skip/reduce/collect/findFirst/anyMatch...), IntStream
 * (range/rangeClosed/map/sum/boxed), and Collectors (joining/groupingBy). Every
 * method returns a deterministic int checked byte-for-byte against real java.
 * Exercises lambdas + java.util.function against clean-room jbase bytecode.
 */
public class DiffStream {

    static int mapFilterSum() {
        Object r = Stream.of(1, 2, 3, 4, 5, 6)
                .filter(x -> ((Integer) x) % 2 == 0)
                .map(x -> ((Integer) x) * 10)
                .reduce(0, (a, b) -> ((Integer) a) + ((Integer) b));
        return (Integer) r; // (2+4+6)*10 = 120
    }

    static int countDistinct() {
        return (int) Stream.of(1, 2, 2, 3, 3, 3).distinct().count(); // 3
    }

    static int sortedLimit() {
        Object r = Stream.of(5, 3, 9, 1, 7)
                .sorted()
                .limit(3)
                .reduce(0, (a, b) -> ((Integer) a) + ((Integer) b));
        return (Integer) r; // 1+3+5 = 9
    }

    static int intRange() {
        return IntStream.range(1, 11).sum(); // 1..10 = 55
    }

    static int intRangeClosedSq() {
        return IntStream.rangeClosed(1, 5).map(x -> x * x).sum(); // 1+4+9+16+25 = 55
    }

    static int mapToIntLen() {
        return Stream.of("a", "bb", "ccc").mapToInt(s -> ((String) s).length()).sum(); // 6
    }

    static int reduceMax() {
        Object r = Stream.of(3, 1, 4, 1, 5)
                .reduce((a, b) -> ((Integer) a) >= ((Integer) b) ? a : b)
                .get();
        return (Integer) r; // 5
    }

    static int joiningLen() {
        String s = (String) Stream.of("a", "b", "c").collect(Collectors.joining("-"));
        return s.length(); // "a-b-c" = 5
    }

    static int groupingSize() {
        Map m = (Map) Stream.of(1, 2, 3, 4, 5, 6)
                .collect(Collectors.groupingBy(x -> ((Integer) x) % 2));
        return m.size(); // keys {0,1} -> 2
    }

    static int skipCount() {
        return (int) Stream.of(1, 2, 3, 4, 5, 6).skip(2).count(); // 4
    }

    static int findFirstEven() {
        Object r = Stream.of(3, 5, 4, 7, 6)
                .filter(x -> ((Integer) x) % 2 == 0)
                .findFirst()
                .get();
        return (Integer) r; // 4
    }

    static int matchFlags() {
        int any = Stream.of(1, 2, 3).anyMatch(x -> ((Integer) x) > 4) ? 1 : 0;   // 0
        int all = Stream.of(1, 2, 3).allMatch(x -> ((Integer) x) > 0) ? 1 : 0;   // 1
        int none = Stream.of(1, 2, 3).noneMatch(x -> ((Integer) x) > 5) ? 1 : 0; // 1
        return any * 100 + all * 10 + none; // 011 -> 11
    }

    static int boxedSum() {
        Object r = IntStream.rangeClosed(1, 4)
                .boxed()
                .reduce(0, (a, b) -> ((Integer) a) + ((Integer) b));
        return (Integer) r; // 1+2+3+4 = 10
    }

    static int toListSize() {
        return Stream.of(9, 8, 7, 6, 5).filter(x -> ((Integer) x) > 6).toList().size(); // {9,8,7} -> 3
    }
}
