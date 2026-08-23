import java.util.stream.Stream;
import java.util.function.BinaryOperator;

public class DiffStreamGen {

    private static final BinaryOperator SUM = new BinaryOperator() {
        public Object apply(Object a, Object b) {
            return Integer.valueOf(((Integer) a).intValue() + ((Integer) b).intValue());
        }
    };

    private static int sum(Stream s) {
        Object r = s.reduce(Integer.valueOf(0), SUM);
        return ((Integer) r).intValue();
    }

    // Stream.iterate(1, x->x*2).limit(6) -> sum = 1+2+4+8+16+32 = 63
    public static int iterateDouble() {
        Stream s = Stream.iterate(Integer.valueOf(1), x -> Integer.valueOf(((Integer) x).intValue() * 2)).limit(6);
        return sum(s);
    }

    // Stream.generate(()->7).limit(4) -> count = 4
    public static int generateCount() {
        long c = Stream.generate(() -> Integer.valueOf(7)).limit(4).count();
        return (int) c;
    }

    // Stream.generate(()->7).limit(4) -> sum = 28
    public static int generateSum() {
        Stream s = Stream.generate(() -> Integer.valueOf(7)).limit(4);
        return sum(s);
    }

    // Stream.iterate(0, x->x+3).limit(5) -> sum = 0+3+6+9+12 = 30
    public static int iterateAdd3() {
        Stream s = Stream.iterate(Integer.valueOf(0), x -> Integer.valueOf(((Integer) x).intValue() + 3)).limit(5);
        return sum(s);
    }

    // limit(0) on generate -> empty -> count 0
    public static int generateLimitZeroCount() {
        return (int) Stream.generate(() -> Integer.valueOf(9)).limit(0).count();
    }

    // iterate limit(1) -> just the seed
    public static int iterateLimitOne() {
        Stream s = Stream.iterate(Integer.valueOf(42), x -> Integer.valueOf(((Integer) x).intValue() + 1)).limit(1);
        return sum(s);
    }

    // iterate then map then sum: iterate(1,x->x+1).limit(4) = [1,2,3,4], map *10 -> [10,20,30,40] sum 100
    public static int iterateMapSum() {
        Stream s = Stream.iterate(Integer.valueOf(1), x -> Integer.valueOf(((Integer) x).intValue() + 1))
                .limit(4)
                .map(x -> Integer.valueOf(((Integer) x).intValue() * 10));
        return sum(s);
    }

    // generate count via limit large-ish
    public static int generateCountTen() {
        return (int) Stream.generate(() -> Integer.valueOf(1)).limit(10).count();
    }

    // iterate powers, take 3: 1+2+4 = 7
    public static int iterateThree() {
        Stream s = Stream.iterate(Integer.valueOf(1), x -> Integer.valueOf(((Integer) x).intValue() * 2)).limit(3);
        return sum(s);
    }
}
