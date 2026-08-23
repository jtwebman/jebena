import java.util.List;
import java.util.Map;
import java.util.IntSummaryStatistics;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DiffColl2 {

    // groupingBy(classifier, counting()) -> read even/odd counts
    public static int caseGroupCounts() {
        Stream s = Stream.of(1, 2, 3, 4, 5, 6, 7);
        Function classifier = x -> Integer.valueOf(((Integer) x).intValue() % 2);
        Map m = (Map) s.collect(Collectors.groupingBy(classifier, Collectors.counting()));
        Long even = (Long) m.get(Integer.valueOf(0));
        Long odd = (Long) m.get(Integer.valueOf(1));
        return (int) (even.longValue() * 100 + odd.longValue());
    }

    // groupingBy(classifier, summingInt(identity)) -> even/odd sums
    public static int caseGroupSummingInt() {
        Stream s = Stream.of(1, 2, 3, 4, 5, 6);
        Function classifier = x -> Integer.valueOf(((Integer) x).intValue() % 2);
        ToIntFunction id = x -> ((Integer) x).intValue();
        Map m = (Map) s.collect(Collectors.groupingBy(classifier, Collectors.summingInt(id)));
        Integer even = (Integer) m.get(Integer.valueOf(0));
        Integer odd = (Integer) m.get(Integer.valueOf(1));
        return even.intValue() * 100 + odd.intValue();
    }

    // summarizingInt -> getSum*1000 + getMax*100 + getMin*10 + (int)getCount
    public static int caseSummarizing() {
        Stream s = Stream.of(3, 1, 4, 1, 5, 9, 2, 6);
        ToIntFunction id = x -> ((Integer) x).intValue();
        IntSummaryStatistics stats = (IntSummaryStatistics) s.collect(Collectors.summarizingInt(id));
        return (int) (stats.getSum() * 1000 + stats.getMax() * 100 + stats.getMin() * 10 + (int) stats.getCount());
    }

    // reducing(identity, op) -> sum
    public static int caseReducingSum() {
        Stream s = Stream.of(1, 2, 3, 4, 5, 6, 7);
        BinaryOperator add = (a, b) -> Integer.valueOf(((Integer) a).intValue() + ((Integer) b).intValue());
        Integer r = (Integer) s.collect(Collectors.reducing(Integer.valueOf(0), add));
        return r.intValue();
    }

    // reducing(identity, op) -> max
    public static int caseReducingMax() {
        Stream s = Stream.of(3, 1, 4, 1, 5, 9, 2, 6);
        BinaryOperator maxOp = (a, b) -> {
            int av = ((Integer) a).intValue();
            int bv = ((Integer) b).intValue();
            return Integer.valueOf(av > bv ? av : bv);
        };
        Integer r = (Integer) s.collect(Collectors.reducing(Integer.valueOf(Integer.MIN_VALUE), maxOp));
        return r.intValue();
    }

    // toUnmodifiableList() -> size
    public static int caseUnmodifiableSize() {
        Stream s = Stream.of(1, 2, 3, 4, 5, 6, 7);
        List list = (List) s.collect(Collectors.toUnmodifiableList());
        return list.size();
    }

    // averagingLong -> (int)(avg*1000)
    public static int caseAveragingLong() {
        Stream s = Stream.of(1, 2, 3, 4);
        ToLongFunction toLong = x -> (long) ((Integer) x).intValue();
        Double avg = (Double) s.collect(Collectors.averagingLong(toLong));
        return (int) (avg.doubleValue() * 1000);
    }
}
