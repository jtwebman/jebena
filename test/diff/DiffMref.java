import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.ToIntFunction;

// Method references to primitive-signature methods bound to reference SAMs
// (Integer::sum as BiFunction<Integer,Integer,Integer>, Math::abs as Function<Integer,Integer>,
// etc.). The lambda-metafactory boundary must box/unbox: unbox the reference args to the impl's
// primitive params, box the primitive result back to the SAM's reference return. Also covers the
// new Integer/Long sum/max/min statics used as the impl targets.
public class DiffMref {

    public static int sumBinOp() {
        BinaryOperator<Integer> op = Integer::sum;
        return op.apply(20, 22).intValue();
    }

    public static int sumBiFunc() {
        BiFunction<Integer, Integer, Integer> f = Integer::sum;
        return f.apply(100, 23).intValue();
    }

    public static int maxBinOp() {
        BinaryOperator<Integer> op = Integer::max;
        return op.apply(7, 19).intValue();
    }

    public static int minBinOp() {
        BinaryOperator<Integer> op = Integer::min;
        return op.apply(7, 19).intValue();
    }

    public static int longSumBinOp() {
        BinaryOperator<Long> op = Long::sum;
        return op.apply(1_000_000L, 2_000_001L).intValue();
    }

    public static int longMaxBinOp() {
        BinaryOperator<Long> op = Long::max;
        return op.apply(555L, 999L).intValue();
    }

    public static int longMinBinOp() {
        BinaryOperator<Long> op = Long::min;
        return op.apply(555L, 999L).intValue();
    }

    public static int absFunc() {
        Function<Integer, Integer> f = Math::abs;
        return f.apply(-15).intValue() + f.apply(15).intValue();
    }

    public static int strLenToInt() {
        ToIntFunction<String> f = String::length;
        return f.applyAsInt("hello") * 100 + f.applyAsInt("hi");
    }

    public static int mergeSum() {
        Map<String, Integer> m = new HashMap<>();
        String[] keys = {"a", "b", "a", "c", "a", "b"};
        for (String k : keys) {
            m.merge(k, 1, Integer::sum);
        }
        // a=3, b=2, c=1 -> encode as 3*100 + 2*10 + 1 = 321
        return m.get("a") * 100 + m.get("b") * 10 + m.get("c");
    }

    public static int mergeAccumulateValues() {
        Map<String, Integer> m = new HashMap<>();
        m.merge("x", 10, Integer::sum);
        m.merge("x", 32, Integer::sum);
        m.merge("y", 5, Integer::max);
        m.merge("y", 8, Integer::max);
        return m.get("x") * 100 + m.get("y"); // 42*100 + 8 = 4208
    }
}
