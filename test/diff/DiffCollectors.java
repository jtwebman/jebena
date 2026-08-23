import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Differential coverage for the java.util.stream.Collectors reductions that run
 * through the eager single-method collect() path: summingInt/summingLong,
 * averagingInt, toMap, mapping(downstream), partitioningBy, counting, joining.
 * Each method returns a deterministic int checked byte-for-byte against real java.
 */
public class DiffCollectors {

    public static int summingIntCase() {
        Object r = Stream.of(1, 2, 3, 4, 5).collect(Collectors.summingInt(x -> (Integer) x));
        return (Integer) r; // 15
    }

    public static int summingLongCase() {
        Object r = Stream.of(10, 20, 30, 40).collect(Collectors.summingLong(x -> (long) (Integer) x));
        return (int) (long) (Long) r; // 100
    }

    public static int averagingIntCase() {
        // avg of {1,2,4} = 2.3333...  -> scale to int for exact compare
        Object r = Stream.of(1, 2, 4).collect(Collectors.averagingInt(x -> (Integer) x));
        return (int) (((Double) r).doubleValue() * 1000000); // 2333333
    }

    public static int averagingEmptyCase() {
        Object r = Stream.of().collect(Collectors.averagingInt(x -> (Integer) x));
        return (int) (((Double) r).doubleValue() * 1000000); // 0
    }

    public static int toMapCase() {
        Object r = Stream.of("a", "bb", "ccc")
                .collect(Collectors.toMap(s -> s, s -> ((String) s).length()));
        Map m = (Map) r;
        int a = (Integer) m.get("a");
        int b = (Integer) m.get("bb");
        int c = (Integer) m.get("ccc");
        return a * 100 + b * 10 + c; // 1*100 + 2*10 + 3 = 123
    }

    public static int mappingCase() {
        Object r = Stream.of("a", "bb", "ccc")
                .collect(Collectors.mapping(s -> ((String) s).length(), Collectors.toList()));
        List xs = (List) r;
        int acc = 0;
        for (int i = 0; i < xs.size(); i++) {
            acc = acc * 31 + (Integer) xs.get(i);
        }
        return acc; // checksum of [1,2,3]
    }

    public static int partitioningByCase() {
        Object r = Stream.of(1, 2, 3, 4, 5, 6)
                .collect(Collectors.partitioningBy(x -> (Integer) x % 2 == 0));
        Map m = (Map) r;
        List evens = (List) m.get(Boolean.TRUE);
        List odds = (List) m.get(Boolean.FALSE);
        return evens.size() * 10 + odds.size(); // 3 evens, 3 odds -> 33
    }

    public static int partitioningSumCase() {
        Object r = Stream.of(1, 2, 3, 4, 5, 6)
                .collect(Collectors.partitioningBy(x -> (Integer) x > 3));
        Map m = (Map) r;
        List big = (List) m.get(Boolean.TRUE);
        int acc = 0;
        for (int i = 0; i < big.size(); i++) {
            acc += (Integer) big.get(i);
        }
        return acc; // 4+5+6 = 15
    }

    public static int countingCase() {
        Object r = Stream.of(7, 7, 7, 7).collect(Collectors.counting());
        return (int) (long) (Long) r; // 4
    }

    public static int joiningPrefixCase() {
        Object r = Stream.of("a", "b", "c").collect(Collectors.joining(",", "[", "]"));
        String s = (String) r; // "[a,b,c]"
        int acc = 0;
        for (int i = 0; i < s.length(); i++) {
            acc = acc * 31 + s.charAt(i);
        }
        return acc;
    }
}
