import java.util.ArrayList;
import java.util.stream.Collector;
import java.util.stream.Stream;

public class DiffCollectorOf {

    // rolling char checksum of a String -> int
    private static int checksum(String s) {
        int h = 0;
        for (int i = 0; i < s.length(); i++) {
            h = h * 31 + s.charAt(i);
        }
        return h;
    }

    // 4-arg of: StringBuilder join, toString finisher -> "abc"
    public static int sbAbc() {
        Collector c = Collector.of(
                () -> new StringBuilder(),
                (sb, x) -> ((StringBuilder) sb).append(x),
                (a, b) -> a,
                sb -> ((StringBuilder) sb).toString());
        String r = (String) Stream.of("a", "b", "c").collect(c);
        return checksum(r);
    }

    // 4-arg of: sum 1..5 via int[1] accumulator, finisher returns the int
    public static int sum1to5() {
        Collector c = Collector.of(
                () -> new int[1],
                (arr, x) -> ((int[]) arr)[0] += (Integer) x,
                (a, b) -> a,
                arr -> Integer.valueOf(((int[]) arr)[0]));
        Object r = Stream.of(
                Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3),
                Integer.valueOf(4), Integer.valueOf(5)).collect(c);
        return ((Integer) r).intValue();
    }

    // 3-arg of: identity finisher yields the int[] container itself
    public static int sum3argIdentity() {
        Collector c = Collector.of(
                () -> new int[1],
                (arr, x) -> ((int[]) arr)[0] += (Integer) x,
                (a, b) -> a);
        Object r = Stream.of(
                Integer.valueOf(10), Integer.valueOf(20), Integer.valueOf(30))
                .collect(c);
        return ((int[]) r)[0];
    }

    // 3-arg of: accumulate into ArrayList, return size
    public static int listSize3arg() {
        Collector c = Collector.of(
                () -> new ArrayList(),
                (lst, x) -> ((ArrayList) lst).add(x),
                (a, b) -> a);
        Object r = Stream.of("p", "q", "r", "s").collect(c);
        return ((ArrayList) r).size();
    }

    // 4-arg of over empty stream -> supplier's identity
    public static int emptySum() {
        Collector c = Collector.of(
                () -> new int[1],
                (arr, x) -> ((int[]) arr)[0] += (Integer) x,
                (a, b) -> a,
                arr -> Integer.valueOf(((int[]) arr)[0]));
        Object r = Stream.of(new Object[0]).collect(c);
        return ((Integer) r).intValue();
    }

    // 4-arg of: product 1..4 -> 24
    public static int product() {
        Collector c = Collector.of(
                () -> new int[]{1},
                (arr, x) -> ((int[]) arr)[0] *= (Integer) x,
                (a, b) -> a,
                arr -> Integer.valueOf(((int[]) arr)[0]));
        Object r = Stream.of(
                Integer.valueOf(1), Integer.valueOf(2),
                Integer.valueOf(3), Integer.valueOf(4)).collect(c);
        return ((Integer) r).intValue();
    }

    // 4-arg of: concatenate uppercased strings, checksum result
    public static int concatUpper() {
        Collector c = Collector.of(
                () -> new StringBuilder(),
                (sb, x) -> ((StringBuilder) sb).append(((String) x).toUpperCase()),
                (a, b) -> a,
                sb -> ((StringBuilder) sb).toString());
        String r = (String) Stream.of("ab", "cd", "ef").collect(c);
        return checksum(r);
    }

    // 4-arg of: count total characters across strings
    public static int totalChars() {
        Collector c = Collector.of(
                () -> new int[1],
                (arr, x) -> ((int[]) arr)[0] += ((String) x).length(),
                (a, b) -> a,
                arr -> Integer.valueOf(((int[]) arr)[0]));
        Object r = Stream.of("one", "three", "seven").collect(c);
        return ((Integer) r).intValue();
    }
}
