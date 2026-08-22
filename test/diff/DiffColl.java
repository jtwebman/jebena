import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Differential coverage for under-tested java.* breadth: java.time date/time
 * arithmetic, TreeMap/LinkedHashMap ordered iteration, Collections + Arrays
 * helpers. Every method returns a deterministic int checked byte-for-byte against
 * real java by scripts/differential.sh.
 */
public class DiffColl {
    // ---- java.time.LocalDate ----
    static int ldPlus() {
        LocalDate d = LocalDate.of(2020, 2, 28).plusDays(2); // leap year -> Mar 1
        return d.getYear() * 10000 + d.getMonthValue() * 100 + d.getDayOfMonth();
    }

    static int ldMinus() {
        LocalDate d = LocalDate.of(2021, 3, 1).minusDays(1); // non-leap -> Feb 28
        return d.getMonthValue() * 100 + d.getDayOfMonth();
    }

    static int ldLeap() {
        return (LocalDate.of(2020, 1, 1).isLeapYear() ? 1 : 0)
                + (LocalDate.of(2021, 1, 1).isLeapYear() ? 10 : 0)
                + (LocalDate.of(2000, 1, 1).isLeapYear() ? 100 : 0)
                + (LocalDate.of(1900, 1, 1).isLeapYear() ? 1000 : 0);
    }

    static int ldDayOfYear() {
        return LocalDate.of(2020, 12, 31).getDayOfYear(); // 366 (leap)
    }

    static int ldPlusMonths() {
        LocalDate d = LocalDate.of(2020, 1, 31).plusMonths(1); // clamps to Feb 29
        return d.getMonthValue() * 100 + d.getDayOfMonth();
    }

    // ---- java.time.Duration / Period ----
    static int durMinutes() {
        Duration d = Duration.ofHours(2).plusMinutes(30);
        return (int) d.toMinutes(); // 150
    }

    static int durCompare() {
        Duration a = Duration.ofSeconds(90);
        Duration b = Duration.ofMinutes(1);
        return a.compareTo(b) > 0 ? 1 : 0; // 90s > 60s -> 1
    }

    // ---- java.time.LocalTime ----
    static int ltPlus() {
        LocalTime t = LocalTime.of(23, 30).plusMinutes(45); // wraps -> 00:15
        return t.getHour() * 100 + t.getMinute();
    }

    // ---- TreeMap ordered iteration + navigation ----
    static int treeOrder() {
        TreeMap m = new TreeMap();
        int[] keys = { 5, 1, 9, 3, 7, 2, 8 };
        for (int k : keys) {
            m.put(Integer.valueOf(k), Integer.valueOf(k * k));
        }
        int acc = 0;
        for (Object e : m.entrySet()) {
            Map.Entry en = (Map.Entry) e;
            acc = acc * 10 + ((Integer) en.getKey()).intValue(); // keys in sorted order
        }
        return acc; // 1234 5789 pattern -> 1235789
    }

    static int treeNav() {
        TreeMap m = new TreeMap();
        for (int k = 0; k <= 100; k += 10) {
            m.put(Integer.valueOf(k), Integer.valueOf(k));
        }
        int first = ((Integer) m.firstKey()).intValue();
        int last = ((Integer) m.lastKey()).intValue();
        return first + last; // 0 + 100
    }

    // ---- LinkedHashMap insertion order ----
    static int lhmOrder() {
        LinkedHashMap m = new LinkedHashMap();
        int[] keys = { 7, 3, 9, 1, 5 };
        for (int k : keys) {
            m.put(Integer.valueOf(k), Integer.valueOf(k));
        }
        int acc = 0;
        for (Object k : m.keySet()) {
            acc = acc * 10 + ((Integer) k).intValue(); // insertion order preserved
        }
        return acc; // 73915
    }

    // ---- Collections helpers ----
    static int collSort() {
        ArrayList<Integer> a = new ArrayList<>();
        int[] v = { 5, 2, 8, 1, 9, 3 };
        for (int x : v) {
            a.add(Integer.valueOf(x));
        }
        Collections.sort(a);
        int acc = 0;
        for (Integer x : a) {
            acc = acc * 10 + x.intValue();
        }
        return acc; // 123589
    }

    static int collMaxMin() {
        List<Integer> a = Arrays.asList(4, 8, 2, 16, 1);
        return Collections.max(a).intValue() * 100 + Collections.min(a).intValue(); // 1601
    }

    static int collReverse() {
        ArrayList<Integer> a = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            a.add(Integer.valueOf(i));
        }
        Collections.reverse(a);
        int acc = 0;
        for (Integer x : a) {
            acc = acc * 10 + x.intValue();
        }
        return acc; // 54321
    }

    // ---- Arrays helpers ----
    static int arrSortSearch() {
        int[] a = { 9, 4, 7, 1, 5, 3 };
        Arrays.sort(a);
        return Arrays.binarySearch(a, 7); // index of 7 in sorted {1,3,4,5,7,9} -> 4
    }

    static int arrCopyRange() {
        int[] a = { 10, 20, 30, 40, 50 };
        int[] b = Arrays.copyOfRange(a, 1, 4); // {20,30,40}
        return b[0] + b[1] + b[2]; // 90
    }
}
