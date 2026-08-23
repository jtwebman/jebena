import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class DiffStream2 {

    // flatMap: each n -> Stream.of(n, n), summing all -> 2*(1+2+3+4+5)=30
    public static int flatMapSum() {
        Stream s = Stream.of(Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3),
                Integer.valueOf(4), Integer.valueOf(5));
        Stream flat = s.flatMap(new Function() {
            public Object apply(Object o) {
                return Stream.of(o, o);
            }
        });
        int sum = 0;
        List out = flat.toList();
        for (int i = 0; i < out.size(); i++) {
            sum += ((Integer) out.get(i)).intValue();
        }
        return sum;
    }

    // flatMap where element 2 maps to an empty stream (contributes nothing);
    // others map to Stream.of(o, o) -> count 1->2, 2->0, 3->2 = 4
    public static int flatMapEmpty() {
        Stream s = Stream.of(Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3));
        Stream flat = s.flatMap(new Function() {
            public Object apply(Object o) {
                if (((Integer) o).intValue() == 2) {
                    return Stream.of();
                }
                return Stream.of(o, o);
            }
        });
        return (int) flat.count();
    }

    // concat count of two streams
    public static int concatCount() {
        Stream a = Stream.of(Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3));
        Stream b = Stream.of(Integer.valueOf(4), Integer.valueOf(5));
        return (int) Stream.concat(a, b).count();
    }

    // concat with an empty stream, summing
    public static int concatEmpty() {
        Stream a = Stream.of();
        Stream b = Stream.of(Integer.valueOf(7), Integer.valueOf(8));
        Stream c = Stream.concat(a, b);
        int sum = 0;
        List out = c.toList();
        for (int i = 0; i < out.size(); i++) {
            sum += ((Integer) out.get(i)).intValue();
        }
        return sum;
    }

    // takeWhile x < 5, summing taken elements -> 1+2+3 = 6
    public static int takeWhileSum() {
        Stream s = Stream.of(Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3),
                Integer.valueOf(7), Integer.valueOf(2), Integer.valueOf(9));
        Stream t = s.takeWhile(new Predicate() {
            public boolean test(Object o) {
                return ((Integer) o).intValue() < 5;
            }
        });
        int sum = 0;
        List out = t.toList();
        for (int i = 0; i < out.size(); i++) {
            sum += ((Integer) out.get(i)).intValue();
        }
        return sum;
    }

    // takeWhile where first element fails -> empty
    public static int takeWhileNone() {
        Stream s = Stream.of(Integer.valueOf(9), Integer.valueOf(1), Integer.valueOf(2));
        Stream t = s.takeWhile(new Predicate() {
            public boolean test(Object o) {
                return ((Integer) o).intValue() < 5;
            }
        });
        return (int) t.count();
    }

    // dropWhile x < 5, summing remaining -> 7+2+9 = 18
    public static int dropWhileSum() {
        Stream s = Stream.of(Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3),
                Integer.valueOf(7), Integer.valueOf(2), Integer.valueOf(9));
        Stream t = s.dropWhile(new Predicate() {
            public boolean test(Object o) {
                return ((Integer) o).intValue() < 5;
            }
        });
        int sum = 0;
        List out = t.toList();
        for (int i = 0; i < out.size(); i++) {
            sum += ((Integer) out.get(i)).intValue();
        }
        return sum;
    }

    // dropWhile where all elements match -> empty
    public static int dropWhileAll() {
        Stream s = Stream.of(Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3));
        Stream t = s.dropWhile(new Predicate() {
            public boolean test(Object o) {
                return ((Integer) o).intValue() < 5;
            }
        });
        return (int) t.count();
    }

    // min via natural order -> -3
    public static int minNat() {
        Stream s = Stream.of(Integer.valueOf(5), Integer.valueOf(2), Integer.valueOf(8),
                Integer.valueOf(-3), Integer.valueOf(4));
        Optional o = s.min(Comparator.naturalOrder());
        return ((Integer) o.get()).intValue();
    }

    // max via natural order -> 8
    public static int maxNat() {
        Stream s = Stream.of(Integer.valueOf(5), Integer.valueOf(2), Integer.valueOf(8),
                Integer.valueOf(-3), Integer.valueOf(4));
        Optional o = s.max(Comparator.naturalOrder());
        return ((Integer) o.get()).intValue();
    }

    // min on empty -> Optional.empty (present flag 0)
    public static int minEmpty() {
        Stream s = Stream.of();
        Optional o = s.min(Comparator.naturalOrder());
        return o.isPresent() ? 1 : 0;
    }
}
