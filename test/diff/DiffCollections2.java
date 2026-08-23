import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Differential coverage for Collections extensions: unmodifiable wrappers,
 * emptySet/emptyMap/singleton, binarySearch(Comparator), max/min(Comparator),
 * disjoint, addAll, fill. Each case returns a deterministic int checked
 * byte-for-byte against real java.
 */
public class DiffCollections2 {

    static int umListSizeGet() {
        List base = new ArrayList();
        base.add(Integer.valueOf(7));
        base.add(Integer.valueOf(9));
        base.add(Integer.valueOf(11));
        List u = Collections.unmodifiableList(base);
        int size = u.size();
        int elem = ((Integer) u.get(1)).intValue();
        return size * 100 + elem; // 3*100 + 9 = 309
    }

    static int umListMutateThrows() {
        List base = new ArrayList();
        base.add(Integer.valueOf(1));
        List u = Collections.unmodifiableList(base);
        try {
            u.set(0, Integer.valueOf(2));
            return 0;
        } catch (UnsupportedOperationException e) {
            return 1;
        }
    }

    static int umSetMutateThrows() {
        Set base = new HashSet();
        base.add(Integer.valueOf(5));
        Set u = Collections.unmodifiableSet(base);
        try {
            u.add(Integer.valueOf(6));
            return 0;
        } catch (UnsupportedOperationException e) {
            return 1;
        }
    }

    static int umMapMutateThrows() {
        Map base = new java.util.HashMap();
        base.put("a", Integer.valueOf(1));
        Map u = Collections.unmodifiableMap(base);
        try {
            u.put("b", Integer.valueOf(2));
            return 0;
        } catch (UnsupportedOperationException e) {
            return 1;
        }
    }

    static int binarySearchComparator() {
        List list = new ArrayList();
        list.add(Integer.valueOf(2));
        list.add(Integer.valueOf(4));
        list.add(Integer.valueOf(6));
        list.add(Integer.valueOf(8));
        list.add(Integer.valueOf(10));
        Comparator c = Comparator.naturalOrder();
        int found = Collections.binarySearch(list, Integer.valueOf(6), c); // index 2
        int missing = Collections.binarySearch(list, Integer.valueOf(5), c); // -(2+1) = -3
        return found * 100 + (-missing); // 2*100 + 3 = 203
    }

    static int maxReverse() {
        Collection coll = new ArrayList();
        ((List) coll).add(Integer.valueOf(3));
        ((List) coll).add(Integer.valueOf(1));
        ((List) coll).add(Integer.valueOf(4));
        ((List) coll).add(Integer.valueOf(2));
        // max under reverse order == min under natural order == 1
        Object m = Collections.max(coll, Comparator.reverseOrder());
        return ((Integer) m).intValue();
    }

    static int minReverse() {
        Collection coll = new ArrayList();
        ((List) coll).add(Integer.valueOf(3));
        ((List) coll).add(Integer.valueOf(1));
        ((List) coll).add(Integer.valueOf(4));
        ((List) coll).add(Integer.valueOf(2));
        // min under reverse order == max under natural order == 4
        Object m = Collections.min(coll, Comparator.reverseOrder());
        return ((Integer) m).intValue();
    }

    static int disjointTrueFalse() {
        Collection a = new ArrayList();
        ((List) a).add(Integer.valueOf(1));
        ((List) a).add(Integer.valueOf(2));
        Collection b = new ArrayList();
        ((List) b).add(Integer.valueOf(3));
        ((List) b).add(Integer.valueOf(4));
        Collection c = new ArrayList();
        ((List) c).add(Integer.valueOf(2));
        ((List) c).add(Integer.valueOf(9));
        int t = Collections.disjoint(a, b) ? 1 : 0; // 1
        int f = Collections.disjoint(a, c) ? 1 : 0; // 0 (shares 2)
        return t * 10 + f; // 10
    }

    static int emptySetMapSize() {
        Set s = Collections.emptySet();
        Map m = Collections.emptyMap();
        return s.size() * 10 + m.size(); // 0
    }

    static int addAllSize() {
        Collection c = new ArrayList();
        ((List) c).add(Integer.valueOf(1));
        boolean changed = Collections.addAll(c, Integer.valueOf(2), Integer.valueOf(3), Integer.valueOf(4));
        return (changed ? 1000 : 0) + c.size(); // 1000 + 4 = 1004
    }

    static int fillCheck() {
        List list = new ArrayList();
        list.add(Integer.valueOf(1));
        list.add(Integer.valueOf(2));
        list.add(Integer.valueOf(3));
        Collections.fill(list, Integer.valueOf(7));
        int sum = 0;
        for (int i = 0; i < list.size(); i++) {
            sum += ((Integer) list.get(i)).intValue();
        }
        return sum; // 21
    }

    static int singletonSize() {
        Set s = Collections.singleton(Integer.valueOf(42));
        int size = s.size();
        int has = s.contains(Integer.valueOf(42)) ? 1 : 0;
        return size * 10 + has; // 11
    }
}
