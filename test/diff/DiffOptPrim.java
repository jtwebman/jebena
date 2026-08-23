import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.OptionalDouble;

public class DiffOptPrim {

    private static int counter = 0;

    public static int streamPresentSum() {
        return OptionalInt.of(5).stream().sum();
    }

    public static int streamEmptySum() {
        return OptionalInt.empty().stream().sum();
    }

    public static int ifPresentSideEffect() {
        counter = 0;
        OptionalInt.of(7).ifPresent(x -> counter += x);
        OptionalInt.empty().ifPresent(x -> counter += 100);
        return counter;
    }

    public static int ifPresentOrElsePresent() {
        int[] mark = new int[1];
        OptionalInt.of(9).ifPresentOrElse(x -> mark[0] = 1, () -> mark[0] = 2);
        return mark[0];
    }

    public static int ifPresentOrElseEmpty() {
        int[] mark = new int[1];
        OptionalInt.empty().ifPresentOrElse(x -> mark[0] = 1, () -> mark[0] = 2);
        return mark[0];
    }

    public static int orElseGetDoublePresent() {
        double d = OptionalDouble.of(3.5).orElseGet(() -> 0.0);
        return (int) (d * 10);
    }

    public static int orElseGetDoubleEmpty() {
        double d = OptionalDouble.empty().orElseGet(() -> 4.2);
        return (int) (d * 10);
    }

    public static int orElseGetInt() {
        return OptionalInt.empty().orElseGet(() -> 42);
    }

    public static int longStreamSum() {
        return (int) OptionalLong.of(11L).stream().sum();
    }

    public static int orElseThrowCatch() {
        try {
            OptionalLong.empty().orElseThrow();
            return 0;
        } catch (java.util.NoSuchElementException e) {
            return 1;
        }
    }
}
