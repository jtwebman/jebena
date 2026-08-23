import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Iterator;

public class DiffLru {

    // Size-bounded LRU cache built on LinkedHashMap's removeEldestEntry hook.
    static class Lru extends LinkedHashMap {
        private final int max;

        Lru(int cap, boolean accessOrder, int max) {
            super(cap, 0.75f, accessOrder);
            this.max = max;
        }

        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > max;
        }
    }

    // Checksum of the current key order (keys are single-char Strings).
    private static int orderChecksum(LinkedHashMap m) {
        int acc = 0;
        Iterator it = m.keySet().iterator();
        while (it.hasNext()) {
            String k = (String) it.next();
            acc = acc * 31 + k.charAt(0);
        }
        return acc;
    }

    private static int valueChecksum(LinkedHashMap m) {
        int acc = 0;
        Iterator it = m.values().iterator();
        while (it.hasNext()) {
            Integer v = (Integer) it.next();
            acc = acc * 31 + v.intValue();
        }
        return acc;
    }

    // Access-order map: get(A) moves A to the tail; order becomes B, C, A.
    public static int accessOrderMovesToEnd() {
        LinkedHashMap m = new LinkedHashMap(16, 0.75f, true);
        m.put("A", Integer.valueOf(1));
        m.put("B", Integer.valueOf(2));
        m.put("C", Integer.valueOf(3));
        m.get("A");
        return orderChecksum(m); // expect order B,C,A
    }

    // Insertion-order map (default ctor): get() must NOT reorder.
    public static int insertionOrderUnchangedByGet() {
        LinkedHashMap m = new LinkedHashMap();
        m.put("A", Integer.valueOf(1));
        m.put("B", Integer.valueOf(2));
        m.put("C", Integer.valueOf(3));
        m.get("A");
        m.get("A");
        m.get("B");
        return orderChecksum(m); // expect order A,B,C
    }

    // Re-putting an existing key in access-order mode moves it to the tail.
    public static int accessOrderReputMovesToEnd() {
        LinkedHashMap m = new LinkedHashMap(16, 0.75f, true);
        m.put("A", Integer.valueOf(1));
        m.put("B", Integer.valueOf(2));
        m.put("C", Integer.valueOf(3));
        m.put("A", Integer.valueOf(9)); // re-put -> A to tail
        return orderChecksum(m) * 100 + ((Integer) m.get("A")).intValue();
    }

    // Re-putting in insertion-order mode keeps position, updates value.
    public static int insertionOrderReputKeepsPosition() {
        LinkedHashMap m = new LinkedHashMap();
        m.put("A", Integer.valueOf(1));
        m.put("B", Integer.valueOf(2));
        m.put("C", Integer.valueOf(3));
        m.put("A", Integer.valueOf(9)); // value update, no move
        return orderChecksum(m) * 100 + ((Integer) m.get("A")).intValue();
    }

    // LRU max 3, insertion order: 4th put evicts the eldest (A).
    public static int lruEvictsEldest() {
        Lru m = new Lru(16, false, 3);
        m.put("A", Integer.valueOf(1));
        m.put("B", Integer.valueOf(2));
        m.put("C", Integer.valueOf(3));
        m.put("D", Integer.valueOf(4)); // evict A
        return m.size() * 100000 + orderChecksum(m); // expect B,C,D size 3
    }

    // After eviction the eldest key is gone.
    public static int lruEvictedKeyAbsent() {
        Lru m = new Lru(16, false, 3);
        m.put("A", Integer.valueOf(1));
        m.put("B", Integer.valueOf(2));
        m.put("C", Integer.valueOf(3));
        m.put("D", Integer.valueOf(4));
        int a = m.containsKey("A") ? 1 : 0;
        int d = m.containsKey("D") ? 1 : 0;
        return a * 10 + d; // expect 01
    }

    // Access-order LRU: touching A protects it; a later put evicts B instead.
    public static int lruAccessProtectsEntry() {
        Lru m = new Lru(16, true, 3);
        m.put("A", Integer.valueOf(1));
        m.put("B", Integer.valueOf(2));
        m.put("C", Integer.valueOf(3));
        m.get("A");                       // order -> B,C,A
        m.put("D", Integer.valueOf(4));   // size 4 -> evict eldest B -> C,A,D
        return m.size() * 100000 + orderChecksum(m);
    }

    // Access-order LRU keeps size capped over many inserts.
    public static int lruSizeCapped() {
        Lru m = new Lru(16, true, 3);
        for (int i = 0; i < 10; i++) {
            m.put("K" + i, Integer.valueOf(i));
        }
        return m.size(); // expect 3
    }

    // Access order also governs values() iteration.
    public static int valuesFollowAccessOrder() {
        LinkedHashMap m = new LinkedHashMap(16, 0.75f, true);
        m.put("A", Integer.valueOf(10));
        m.put("B", Integer.valueOf(20));
        m.put("C", Integer.valueOf(30));
        m.get("B"); // order -> A,C,B
        return valueChecksum(m); // expect 10,30,20
    }

    // Default map without removeEldestEntry override never evicts.
    public static int defaultNoEviction() {
        LinkedHashMap m = new LinkedHashMap();
        for (int i = 0; i < 5; i++) {
            m.put("K" + i, Integer.valueOf(i));
        }
        return m.size(); // expect 5
    }

    // Classic LRU sequence: get keeps recent, evictions follow use.
    public static int lruSequence() {
        Lru m = new Lru(16, true, 2);
        m.put("A", Integer.valueOf(1)); // A
        m.put("B", Integer.valueOf(2)); // A,B
        m.get("A");                     // B,A
        m.put("C", Integer.valueOf(3)); // evict B -> A,C
        int bGone = m.containsKey("B") ? 0 : 1;
        int aHere = m.containsKey("A") ? 1 : 0;
        return bGone * 1000 + aHere * 100 + m.size() * 10 + orderChecksum(m) % 10;
    }
}
