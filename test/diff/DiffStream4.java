import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class DiffStream4 {

    static int checksum(List list) {
        int h = 0;
        for (int i = 0; i < list.size(); i++) {
            h = h * 31 + ((Integer) list.get(i)).intValue();
        }
        return h;
    }

    static int strChecksum(String s) {
        int h = 0;
        for (int i = 0; i < s.length(); i++) {
            h = h * 31 + s.charAt(i);
        }
        return h;
    }

    public static int collectSize() {
        ArrayList out = (ArrayList) Stream.of(1, 2, 3, 4).collect(
                () -> new ArrayList(),
                (c, e) -> { ((ArrayList) c).add(e); },
                (a, b) -> { ((ArrayList) a).addAll((ArrayList) b); });
        return out.size();
    }

    public static int collectChecksum() {
        ArrayList out = (ArrayList) Stream.of(1, 2, 3, 4).collect(
                () -> new ArrayList(),
                (c, e) -> { ((ArrayList) c).add(e); },
                (a, b) -> { ((ArrayList) a).addAll((ArrayList) b); });
        return checksum(out);
    }

    public static int collectEmpty() {
        ArrayList out = (ArrayList) Stream.of().collect(
                () -> new ArrayList(),
                (c, e) -> { ((ArrayList) c).add(e); },
                (a, b) -> { ((ArrayList) a).addAll((ArrayList) b); });
        return out.size();
    }

    public static int collectStringBuilder() {
        StringBuilder sb = (StringBuilder) Stream.of("a", "bb", "ccc").collect(
                () -> new StringBuilder(),
                (c, e) -> { ((StringBuilder) c).append((String) e); },
                (a, b) -> { ((StringBuilder) a).append((StringBuilder) b); });
        return strChecksum(sb.toString());
    }

    public static int collectMapped() {
        ArrayList out = (ArrayList) Stream.of(1, 2, 3, 4)
                .map(x -> ((Integer) x).intValue() * 2)
                .collect(
                        () -> new ArrayList(),
                        (c, e) -> { ((ArrayList) c).add(e); },
                        (a, b) -> { ((ArrayList) a).addAll((ArrayList) b); });
        return checksum(out);
    }

    public static int toArrayLength() {
        Integer[] arr = (Integer[]) Stream.of(1, 2, 3, 4, 5).toArray(Integer[]::new);
        return arr.length;
    }

    public static int toArrayChecksum() {
        Integer[] arr = (Integer[]) Stream.of(10, 20, 30).toArray(Integer[]::new);
        int h = 0;
        for (int i = 0; i < arr.length; i++) {
            h = h * 31 + arr[i].intValue();
        }
        return h;
    }

    public static int toArrayEmpty() {
        Integer[] arr = (Integer[]) Stream.of().toArray(Integer[]::new);
        return arr.length;
    }
}
