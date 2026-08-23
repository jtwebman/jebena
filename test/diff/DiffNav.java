import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Differential coverage for navigable collections + Optional: TreeMap
 * ceiling/floor/higher/lowerKey + first/lastKey, TreeSet ceiling/floor/higher/
 * lower/first/last, and java.util.Optional of/ofNullable/empty/map/filter/orElse/
 * orElseGet/ifPresentOrElse/or. Each case returns a deterministic int vs real java.
 */
public class DiffNav {
    private static TreeMap tm() {
        TreeMap m = new TreeMap();
        int[] ks = { 10, 20, 30, 40, 50 };
        for (int k : ks) {
            m.put(Integer.valueOf(k), Integer.valueOf(k * k));
        }
        return m;
    }

    static int tmCeilFloor() {
        TreeMap m = tm();
        int c = ((Integer) m.ceilingKey(Integer.valueOf(25))).intValue(); // 30
        int f = ((Integer) m.floorKey(Integer.valueOf(25))).intValue(); // 20
        return c * 100 + f; // 3020
    }

    static int tmHigherLower() {
        TreeMap m = tm();
        int h = ((Integer) m.higherKey(Integer.valueOf(20))).intValue(); // 30
        int l = ((Integer) m.lowerKey(Integer.valueOf(20))).intValue(); // 10
        return h * 100 + l; // 3010
    }

    static int tmEdges() {
        TreeMap m = tm();
        int first = ((Integer) m.firstKey()).intValue(); // 10
        int last = ((Integer) m.lastKey()).intValue(); // 50
        // ceilingKey below-min = min; floorKey above-max = max
        int cb = ((Integer) m.ceilingKey(Integer.valueOf(5))).intValue(); // 10
        int fa = ((Integer) m.floorKey(Integer.valueOf(99))).intValue(); // 50
        return first + last + cb + fa; // 10+50+10+50 = 120
    }

    static int tmMisses() {
        TreeMap m = tm();
        int r = 0;
        r += (m.ceilingKey(Integer.valueOf(51)) == null) ? 1 : 0; // nothing >= 51
        r = r * 10 + ((m.floorKey(Integer.valueOf(9)) == null) ? 1 : 0); // nothing <= 9
        r = r * 10 + ((m.higherKey(Integer.valueOf(50)) == null) ? 1 : 0); // nothing > 50
        r = r * 10 + ((m.lowerKey(Integer.valueOf(10)) == null) ? 1 : 0); // nothing < 10
        return r; // 1111
    }

    private static TreeSet ts() {
        TreeSet s = new TreeSet();
        int[] vs = { 5, 10, 15, 20, 25 };
        for (int v : vs) {
            s.add(Integer.valueOf(v));
        }
        return s;
    }

    static int tsNav() {
        TreeSet s = ts();
        int ceil = ((Integer) s.ceiling(Integer.valueOf(12))).intValue(); // 15
        int floor = ((Integer) s.floor(Integer.valueOf(12))).intValue(); // 10
        int higher = ((Integer) s.higher(Integer.valueOf(15))).intValue(); // 20
        int lower = ((Integer) s.lower(Integer.valueOf(15))).intValue(); // 10
        return ceil * 1000 + floor * 100 + higher + lower; // 15000 + 1000 + 20 + 10 = 16030
    }

    static int tsEdges() {
        TreeSet s = ts();
        int first = ((Integer) s.first()).intValue(); // 5
        int last = ((Integer) s.last()).intValue(); // 25
        int miss = (s.higher(Integer.valueOf(25)) == null ? 1 : 0)
                + (s.lower(Integer.valueOf(5)) == null ? 1 : 0); // 2
        return first * 100 + last + miss; // 500 + 25 + 2 = 527
    }

    static int optMapFilter() {
        Optional o = Optional.of(Integer.valueOf(21))
                .map(x -> Integer.valueOf(((Integer) x).intValue() * 2)) // 42
                .filter(x -> ((Integer) x).intValue() % 2 == 0); // present
        return ((Integer) o.get()).intValue(); // 42
    }

    static int optOrElse() {
        int a = ((Integer) Optional.ofNullable(null).orElse(Integer.valueOf(99))).intValue(); // 99
        int b = ((Integer) Optional.of(Integer.valueOf(7)).orElse(Integer.valueOf(0))).intValue(); // 7
        return a * 100 + b; // 9907
    }

    static int optOrElseGet() {
        int v = ((Integer) Optional.empty().orElseGet(() -> Integer.valueOf(55))).intValue(); // 55
        return v;
    }

    static int optFilterEmpty() {
        Optional o = Optional.of(Integer.valueOf(3)).filter(x -> ((Integer) x).intValue() > 10);
        return o.isPresent() ? 1 : 0; // 0
    }

    static int optIfPresentOrElse() {
        int[] c = { 0 };
        Optional.of(Integer.valueOf(5)).ifPresentOrElse(v -> c[0] += ((Integer) v).intValue(), () -> c[0] = -1);
        Optional.empty().ifPresentOrElse(v -> c[0] += 100, () -> c[0] += 1000);
        return c[0]; // 5 + 1000 = 1005
    }

    static int optOr() {
        Optional o = Optional.ofNullable(null).or(() -> Optional.of(Integer.valueOf(88)));
        return ((Integer) o.get()).intValue(); // 88
    }
}
