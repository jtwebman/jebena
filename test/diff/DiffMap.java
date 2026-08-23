import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Differential coverage for the Map default methods (getOrDefault/putIfAbsent/merge/
 * compute/computeIfAbsent/forEach) added on the Map interface — inherited by HashMap
 * and TreeMap. Each case returns a deterministic int checked byte-for-byte vs real java.
 */
public class DiffMap {
    static int mergeCount() {
        HashMap m = new HashMap();
        String[] words = { "a", "b", "a", "c", "a", "b" };
        for (String w : words) {
            m.merge(w, Integer.valueOf(1),
                    (p, q) -> Integer.valueOf(((Integer) p).intValue() + ((Integer) q).intValue()));
        }
        return ((Integer) m.get("a")).intValue() * 100
                + ((Integer) m.get("b")).intValue() * 10
                + ((Integer) m.get("c")).intValue(); // a=3,b=2,c=1 -> 321
    }

    static int getOrDefault() {
        HashMap m = new HashMap();
        m.put("k", Integer.valueOf(7));
        return ((Integer) m.getOrDefault("k", Integer.valueOf(0))).intValue() * 100
                + ((Integer) m.getOrDefault("missing", Integer.valueOf(42))).intValue(); // 742
    }

    static int putIfAbsent() {
        HashMap m = new HashMap();
        Object r1 = m.putIfAbsent("k", Integer.valueOf(1)); // null (was absent)
        Object r2 = m.putIfAbsent("k", Integer.valueOf(2)); // returns existing 1, no overwrite
        return (r1 == null ? 1 : 0) * 1000 + ((Integer) r2).intValue() * 10 + ((Integer) m.get("k")).intValue();
        // 1000 + 10 + 1 = 1011
    }

    static int computeIfAbsent() {
        // group values by parity into lists via computeIfAbsent
        TreeMap m = new TreeMap();
        for (int i = 1; i <= 6; i++) {
            ArrayList l = (ArrayList) m.computeIfAbsent(Integer.valueOf(i % 2), k -> new ArrayList());
            l.add(Integer.valueOf(i));
        }
        ArrayList evens = (ArrayList) m.get(Integer.valueOf(0));
        ArrayList odds = (ArrayList) m.get(Integer.valueOf(1));
        return evens.size() * 100 + odds.size() * 10 + ((Integer) odds.get(0)).intValue(); // 3,3,1 -> 331
    }

    static int compute() {
        HashMap m = new HashMap();
        m.put("k", Integer.valueOf(10));
        m.compute("k", (key, v) -> Integer.valueOf(((Integer) v).intValue() * 3)); // 30
        m.compute("k", (key, v) -> null); // removes k
        return ((Integer) m.getOrDefault("k", Integer.valueOf(-1))).intValue() + 1000; // 999
    }

    static int forEachSum() {
        TreeMap m = new TreeMap();
        for (int i = 1; i <= 5; i++) {
            m.put(Integer.valueOf(i), Integer.valueOf(i * i));
        }
        int[] acc = { 0 };
        m.forEach((k, v) -> acc[0] += ((Integer) k).intValue() * 100 + ((Integer) v).intValue());
        return acc[0]; // sum of k*100+v = (1..5)*100 + (1+4+9+16+25) = 1500 + 55 = 1555
    }
}
