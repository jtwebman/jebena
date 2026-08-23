import java.util.TreeMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Iterator;

public class DiffTreeNav {
    private static TreeMap build() {
        TreeMap m = new TreeMap();
        m.put(new Integer(10), new Integer(100));
        m.put(new Integer(20), new Integer(200));
        m.put(new Integer(30), new Integer(300));
        m.put(new Integer(40), new Integer(400));
        return m;
    }

    public static int ceilingEntry25() {
        Map.Entry e = build().ceilingEntry(new Integer(25));
        return ((Integer) e.getKey()).intValue();
    }

    public static int floorEntry25() {
        Map.Entry e = build().floorEntry(new Integer(25));
        return ((Integer) e.getKey()).intValue();
    }

    public static int higherEntry20() {
        Map.Entry e = build().higherEntry(new Integer(20));
        return ((Integer) e.getKey()).intValue();
    }

    public static int lowerEntry20() {
        Map.Entry e = build().lowerEntry(new Integer(20));
        return ((Integer) e.getValue()).intValue();
    }

    public static int higherKey20() {
        return ((Integer) build().higherKey(new Integer(20))).intValue();
    }

    public static int lowerKey20() {
        return ((Integer) build().lowerKey(new Integer(20))).intValue();
    }

    public static int pollFirstEntryKey() {
        Map.Entry e = build().pollFirstEntry();
        return ((Integer) e.getKey()).intValue();
    }

    public static int pollFirstThenFirstKey() {
        TreeMap m = build();
        m.pollFirstEntry();
        return ((Integer) m.firstKey()).intValue();
    }

    public static int pollLastEntryKey() {
        Map.Entry e = build().pollLastEntry();
        return ((Integer) e.getKey()).intValue();
    }

    public static int descendingFirst() {
        Iterator it = build().descendingKeySet().iterator();
        return ((Integer) it.next()).intValue();
    }

    public static int lastEntryValue() {
        Map.Entry e = build().lastEntry();
        return ((Integer) e.getValue()).intValue();
    }

    public static int firstEntryKey() {
        Map.Entry e = build().firstEntry();
        return ((Integer) e.getKey()).intValue();
    }

    public static int descendingMapFirstKey() {
        NavigableMap d = build().descendingMap();
        return ((Integer) d.firstKey()).intValue();
    }

    public static int descendingMapCeiling() {
        NavigableMap d = build().descendingMap();
        // descending ceilingKey(25) == ascending floorKey(25) == 20
        return ((Integer) d.ceilingKey(new Integer(25))).intValue();
    }
}
