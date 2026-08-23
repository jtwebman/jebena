import java.util.Objects;

public class DiffObjects {
    static int sum(String s) {
        int h = 0;
        for (int i = 0; i < s.length(); i++) h = h * 31 + s.charAt(i);
        return h;
    }

    public static int elseNull() {
        String r = (String) Objects.requireNonNullElse(null, "d");
        return sum(r);
    }

    public static int elseNonNull() {
        String r = (String) Objects.requireNonNullElse("x", "d");
        return sum(r);
    }

    public static int elseGetNull() {
        String r = (String) Objects.requireNonNullElseGet(null, () -> "g");
        return sum(r);
    }

    public static int elseGetNonNull() {
        String r = (String) Objects.requireNonNullElseGet("keep", () -> "g");
        return sum(r);
    }

    public static int checkIdx() {
        return Objects.checkIndex(2, 5);
    }

    public static int checkFromTo() {
        return Objects.checkFromToIndex(1, 3, 5);
    }

    public static int checkFromSize() {
        return Objects.checkFromIndexSize(1, 3, 5);
    }

    public static int hashInts() {
        return Objects.hash(1, 2, 3);
    }

    public static int hashMixed() {
        return Objects.hash("a", null, 42, "bc");
    }

    public static int toStringNull() {
        return sum(Objects.toString(null, "n"));
    }

    public static int toStringNonNull() {
        return sum(Objects.toString("val", "n"));
    }

    public static int reqSupplier() {
        Object o = Objects.requireNonNull("ok", () -> "msg");
        return sum((String) o);
    }
}
