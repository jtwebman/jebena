import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

// instanceof / checkcast against TRANSITIVE super-interfaces. TreeMap declares NavigableMap,
// which extends SortedMap, which extends Map -> `treeMap instanceof Map` must be true and
// `(Map) treeMap` must succeed. Verifies the VM walks the interface hierarchy transitively.
public class DiffCast {

    static int b(boolean x) {
        return x ? 1 : 0;
    }

    public static int treeMapInstanceof() {
        Object o = new TreeMap();
        int r = 0;
        r = r * 2 + b(o instanceof TreeMap);
        r = r * 2 + b(o instanceof NavigableMap); // direct
        r = r * 2 + b(o instanceof SortedMap);    // NavigableMap extends SortedMap
        r = r * 2 + b(o instanceof Map);          // SortedMap extends Map (transitive)
        return r; // 1111b = 15
    }

    public static int treeMapCastThenUse() {
        Object o = new TreeMap();
        Map m = (Map) o; // checkcast to transitive super-interface must not throw
        m.put("a", "1");
        m.put("b", "2");
        SortedMap sm = (SortedMap) o;
        return m.size() * 10 + sm.size(); // 22
    }

    public static int hashMapInstanceof() {
        Object o = new HashMap();
        return b(o instanceof Map) * 10 + b(o instanceof HashMap); // 11
    }

    public static int concurrentHashMapInstanceof() {
        Object o = new ConcurrentHashMap();
        int r = 0;
        r = r * 2 + b(o instanceof ConcurrentHashMap);
        r = r * 2 + b(o instanceof ConcurrentMap); // direct
        r = r * 2 + b(o instanceof Map);           // ConcurrentMap extends Map (transitive)
        return r; // 111b = 7
    }

    public static int arrayListInstanceof() {
        Object o = new ArrayList();
        int r = 0;
        r = r * 2 + b(o instanceof ArrayList);
        r = r * 2 + b(o instanceof List);       // direct
        r = r * 2 + b(o instanceof java.util.Collection); // List extends Collection
        r = r * 2 + b(o instanceof Iterable);   // Collection extends Iterable (transitive)
        return r; // 1111b = 15
    }

    public static int arrayListCastThenUse() {
        Object o = new ArrayList();
        java.util.Collection c = (java.util.Collection) o; // transitive-super-interface cast
        c.add("x");
        c.add("y");
        Iterable it = (Iterable) o;
        int n = 0;
        for (Object e : it) {
            n++;
        }
        return c.size() * 10 + n; // 22
    }

    public static int falseCases() {
        Object s = "hello";
        Object list = new ArrayList();
        int r = 0;
        r = r * 2 + b(s instanceof Map);        // 0
        r = r * 2 + b(list instanceof Map);     // 0 (ArrayList is not a Map)
        r = r * 2 + b(s instanceof CharSequence); // 1 (String implements CharSequence)
        return r; // 001b = 1
    }

    public static int nullInstanceof() {
        Object o = null;
        return b(o instanceof Map); // null instanceof anything = 0
    }
}
