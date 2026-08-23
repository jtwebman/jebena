import java.util.Comparator;
import java.util.HashMap;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DiffColl3 {

    static BinaryOperator sumInts() {
        return new BinaryOperator() {
            public Object apply(Object a, Object b) {
                return Integer.valueOf(((Integer) a).intValue() + ((Integer) b).intValue());
            }
        };
    }

    static Function keyModThree() {
        return new Function() {
            public Object apply(Object o) {
                return Integer.valueOf(((Integer) o).intValue() % 3);
            }
        };
    }

    static Function identity() {
        return new Function() {
            public Object apply(Object o) {
                return o;
            }
        };
    }

    static Function oneValue() {
        return new Function() {
            public Object apply(Object o) {
                return Integer.valueOf(1);
            }
        };
    }

    static HashMap mergeSumMap() {
        Stream s = Stream.of(new Object[]{
                Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3),
                Integer.valueOf(4), Integer.valueOf(5), Integer.valueOf(6)});
        return (HashMap) s.collect(Collectors.toMap(keyModThree(), identity(), sumInts()));
    }

    public static int toMapMergeSum() {
        HashMap m = mergeSumMap();
        return ((Integer) m.get(Integer.valueOf(0))).intValue();
    }

    public static int toMapMergeKey1() {
        HashMap m = mergeSumMap();
        return ((Integer) m.get(Integer.valueOf(1))).intValue();
    }

    public static int toMapMergeKey2() {
        HashMap m = mergeSumMap();
        return ((Integer) m.get(Integer.valueOf(2))).intValue();
    }

    public static int toMapMergeCount() {
        Stream s = Stream.of(new Object[]{
                Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3),
                Integer.valueOf(4), Integer.valueOf(5), Integer.valueOf(6)});
        HashMap m = (HashMap) s.collect(Collectors.toMap(keyModThree(), oneValue(), sumInts()));
        return ((Integer) m.get(Integer.valueOf(0))).intValue();
    }

    public static int minByInt() {
        Stream s = Stream.of(new Object[]{
                Integer.valueOf(5), Integer.valueOf(3), Integer.valueOf(8),
                Integer.valueOf(1), Integer.valueOf(9), Integer.valueOf(2)});
        Optional o = (Optional) s.collect(Collectors.minBy(Comparator.naturalOrder()));
        return ((Integer) o.get()).intValue();
    }

    public static int maxByInt() {
        Stream s = Stream.of(new Object[]{
                Integer.valueOf(5), Integer.valueOf(3), Integer.valueOf(8),
                Integer.valueOf(1), Integer.valueOf(9), Integer.valueOf(2)});
        Optional o = (Optional) s.collect(Collectors.maxBy(Comparator.naturalOrder()));
        return ((Integer) o.get()).intValue();
    }

    public static int teeingSumCount() {
        Stream s = Stream.of(new Object[]{
                Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3),
                Integer.valueOf(4), Integer.valueOf(5)});
        BiFunction merger = new BiFunction() {
            public Object apply(Object sum, Object count) {
                return Integer.valueOf(((Integer) sum).intValue() * 1000
                        + (int) ((Long) count).longValue());
            }
        };
        Integer r = (Integer) s.collect(Collectors.teeing(
                Collectors.summingInt(new java.util.function.ToIntFunction() {
                    public int applyAsInt(Object o) {
                        return ((Integer) o).intValue();
                    }
                }),
                Collectors.counting(),
                merger));
        return r.intValue();
    }

    public static int teeingMinMax() {
        Stream s = Stream.of(new Object[]{
                Integer.valueOf(5), Integer.valueOf(3), Integer.valueOf(8),
                Integer.valueOf(1), Integer.valueOf(9), Integer.valueOf(2)});
        BiFunction merger = new BiFunction() {
            public Object apply(Object lo, Object hi) {
                int min = ((Integer) ((Optional) lo).get()).intValue();
                int max = ((Integer) ((Optional) hi).get()).intValue();
                return Integer.valueOf(min * 1000 + max);
            }
        };
        Collector lo = Collectors.minBy(Comparator.naturalOrder());
        Collector hi = Collectors.maxBy(Comparator.naturalOrder());
        Integer r = (Integer) s.collect(Collectors.teeing(lo, hi, merger));
        return r.intValue();
    }
}
