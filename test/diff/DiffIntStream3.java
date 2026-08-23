import java.util.stream.IntStream;

public class DiffIntStream3 {

    private static int acc;

    public static int anyMatchTrue() {
        return IntStream.rangeClosed(1, 10).anyMatch(x -> x == 7) ? 1 : 0;
    }

    public static int anyMatchFalse() {
        return IntStream.rangeClosed(1, 10).anyMatch(x -> x == 99) ? 1 : 0;
    }

    public static int allMatch() {
        return IntStream.rangeClosed(1, 10).allMatch(x -> x > 0) ? 1 : 0;
    }

    public static int allMatchFalse() {
        return IntStream.rangeClosed(1, 10).allMatch(x -> x > 5) ? 1 : 0;
    }

    public static int noneMatch() {
        return IntStream.rangeClosed(1, 10).noneMatch(x -> x > 100) ? 1 : 0;
    }

    public static int findFirst() {
        return IntStream.range(5, 10).findFirst().getAsInt();
    }

    public static int findFirstEmpty() {
        return IntStream.range(5, 5).findFirst().orElse(-1);
    }

    public static int peekSum() {
        int[] counter = new int[1];
        int sum = IntStream.rangeClosed(1, 5).peek(x -> counter[0] += x).sum();
        return sum + counter[0];
    }

    public static int forEachAcc() {
        acc = 0;
        IntStream.rangeClosed(1, 4).forEach(x -> acc += x);
        return acc;
    }
}
