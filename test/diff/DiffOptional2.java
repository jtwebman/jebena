import java.util.Optional;

public class DiffOptional2 {

    public static int filterKeep() {
        return (Integer) Optional.of(5).filter(x -> (Integer) x > 3).get();
    }

    public static int filterDrop() {
        return Optional.of(2).filter(x -> (Integer) x > 3).isPresent() ? 1 : 0;
    }

    public static int flatMapPresent() {
        return (Integer) Optional.of(5).flatMap(x -> Optional.of((Integer) x + 100)).get();
    }

    public static int flatMapEmpty() {
        return Optional.empty().flatMap(x -> Optional.of(x)).isPresent() ? 1 : 0;
    }

    public static int orEmpty() {
        return (Integer) Optional.empty().or(() -> Optional.of(9)).get();
    }

    public static int orPresent() {
        return (Integer) Optional.of(4).or(() -> Optional.of(9)).get();
    }

    public static int streamPresent() {
        return (int) Optional.of(7).stream().count();
    }

    public static int streamEmpty() {
        return (int) Optional.empty().stream().count();
    }
}
