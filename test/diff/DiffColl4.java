import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DiffColl4 {

    public static int collectingAndThenSize() {
        Object r = Stream.of(1, 2, 3, 4).collect(
                Collectors.collectingAndThen(Collectors.toList(), list -> ((List) list).size()));
        return ((Integer) r).intValue();
    }

    public static int collectingAndThenSetSize() {
        Object r = Stream.of(1, 2, 2, 3, 3, 3).collect(
                Collectors.collectingAndThen(Collectors.toSet(), s -> ((Set) s).size()));
        return ((Integer) r).intValue();
    }

    public static int filteringEvens() {
        Object r = Stream.of(1, 2, 3, 4, 5, 6).collect(
                Collectors.filtering(x -> ((Integer) x) % 2 == 0, Collectors.counting()));
        return (int) ((Long) r).longValue();
    }

    public static int filteringNone() {
        Object r = Stream.of(1, 2, 3).collect(
                Collectors.filtering(x -> false, Collectors.counting()));
        return (int) ((Long) r).longValue();
    }

    public static int filteringToList() {
        Object r = Stream.of(1, 2, 3, 4, 5).collect(
                Collectors.filtering(x -> ((Integer) x) > 2, Collectors.toList()));
        return ((List) r).size();
    }

    public static int flatMappingDouble() {
        Object r = Stream.of(1, 2, 3).collect(
                Collectors.flatMapping(x -> Stream.of(x, x), Collectors.counting()));
        return (int) ((Long) r).longValue();
    }

    public static int flatMappingEmpty() {
        Object r = Stream.of(1, 2, 3).collect(
                Collectors.flatMapping(x -> Stream.of(), Collectors.counting()));
        return (int) ((Long) r).longValue();
    }

    public static int flatMappingToList() {
        Object r = Stream.of(1, 2).collect(
                Collectors.flatMapping(x -> Stream.of(x, x, x), Collectors.toList()));
        return ((List) r).size();
    }

    public static int toCollectionSize() {
        Object r = Stream.of(1, 2, 3, 4, 5).collect(
                Collectors.toCollection(() -> new java.util.ArrayList()));
        return ((List) r).size();
    }

    public static int toCollectionDups() {
        Object r = Stream.of(7, 7, 7, 7).collect(
                Collectors.toCollection(() -> new java.util.ArrayList()));
        return ((List) r).size();
    }

    public static int toCollectionSet() {
        Object r = Stream.of(1, 2, 2, 3, 3, 3).collect(
                Collectors.toCollection(() -> new java.util.HashSet()));
        return ((Set) r).size();
    }
}
