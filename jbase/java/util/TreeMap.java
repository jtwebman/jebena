package java.util;

/**
 * Clean-room java.util.TreeMap: an (unbalanced) binary search tree ordered by the
 * keys' natural ordering or a supplied Comparator. Balancing is a future perf
 * improvement; ordering/navigation results already match the spec.
 */
public class TreeMap implements NavigableMap {
    static class Node {
        Object key;
        Object value;
        Node left;
        Node right;

        Node(Object key, Object value) {
            this.key = key;
            this.value = value;
        }
    }

    private Node root;
    private int size;
    private final Comparator comparator;

    public TreeMap() {
        this.comparator = null;
    }

    public TreeMap(Comparator c) {
        this.comparator = c;
    }

    public Comparator comparator() {
        return comparator;
    }

    private int cmp(Object a, Object b) {
        if (comparator != null) {
            return comparator.compare(a, b);
        }
        return ((Comparable) a).compareTo(b);
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    private Node getNode(Object key) {
        Node x = root;
        while (x != null) {
            int c = cmp(key, x.key);
            if (c == 0) {
                return x;
            }
            x = (c < 0) ? x.left : x.right;
        }
        return null;
    }

    public Object get(Object key) {
        Node n = getNode(key);
        return (n == null) ? null : n.value;
    }

    public boolean containsKey(Object key) {
        return getNode(key) != null;
    }

    public Object put(Object key, Object value) {
        if (root == null) {
            root = new Node(key, value);
            size++;
            return null;
        }
        Node x = root;
        while (true) {
            int c = cmp(key, x.key);
            if (c == 0) {
                Object old = x.value;
                x.value = value;
                return old;
            } else if (c < 0) {
                if (x.left == null) {
                    x.left = new Node(key, value);
                    size++;
                    return null;
                }
                x = x.left;
            } else {
                if (x.right == null) {
                    x.right = new Node(key, value);
                    size++;
                    return null;
                }
                x = x.right;
            }
        }
    }

    public Object remove(Object key) {
        Node parent = null;
        Node x = root;
        while (x != null) {
            int c = cmp(key, x.key);
            if (c == 0) {
                break;
            }
            parent = x;
            x = (c < 0) ? x.left : x.right;
        }
        if (x == null) {
            return null;
        }
        Object old = x.value;
        if (x.left != null && x.right != null) {
            Node succParent = x;
            Node succ = x.right;
            while (succ.left != null) {
                succParent = succ;
                succ = succ.left;
            }
            x.key = succ.key;
            x.value = succ.value;
            x = succ;
            parent = succParent;
        }
        Node child = (x.left != null) ? x.left : x.right;
        if (parent == null) {
            root = child;
        } else if (parent.left == x) {
            parent.left = child;
        } else {
            parent.right = child;
        }
        size--;
        return old;
    }

    public boolean containsValue(Object value) {
        ArrayList ks = keys();
        for (int i = 0; i < ks.size(); i++) {
            Object v = get(ks.get(i));
            if (value == null ? v == null : value.equals(v)) {
                return true;
            }
        }
        return false;
    }

    public void clear() {
        root = null;
        size = 0;
    }

    public Object firstKey() {
        if (root == null) {
            throw new NoSuchElementException();
        }
        Node x = root;
        while (x.left != null) {
            x = x.left;
        }
        return x.key;
    }

    public Object lastKey() {
        if (root == null) {
            throw new NoSuchElementException();
        }
        Node x = root;
        while (x.right != null) {
            x = x.right;
        }
        return x.key;
    }

    public Object ceilingKey(Object key) {
        Node x = root;
        Node best = null;
        while (x != null) {
            int c = cmp(key, x.key);
            if (c == 0) {
                return x.key;
            }
            if (c < 0) {
                best = x;
                x = x.left;
            } else {
                x = x.right;
            }
        }
        return (best == null) ? null : best.key;
    }

    public Object floorKey(Object key) {
        Node x = root;
        Node best = null;
        while (x != null) {
            int c = cmp(key, x.key);
            if (c == 0) {
                return x.key;
            }
            if (c > 0) {
                best = x;
                x = x.right;
            } else {
                x = x.left;
            }
        }
        return (best == null) ? null : best.key;
    }

    public Object higherKey(Object key) {
        Node x = root;
        Node best = null;
        while (x != null) {
            int c = cmp(key, x.key);
            if (c < 0) {
                best = x;
                x = x.left;
            } else {
                x = x.right;
            }
        }
        return (best == null) ? null : best.key;
    }

    public Object lowerKey(Object key) {
        Node x = root;
        Node best = null;
        while (x != null) {
            int c = cmp(key, x.key);
            if (c > 0) {
                best = x;
                x = x.right;
            } else {
                x = x.left;
            }
        }
        return (best == null) ? null : best.key;
    }

    // In-order keys (iterative, no recursion depth limit).
    public ArrayList keys() {
        ArrayList out = new ArrayList();
        ArrayList stack = new ArrayList();
        Node x = root;
        while (x != null || !stack.isEmpty()) {
            while (x != null) {
                stack.add(x);
                x = x.left;
            }
            x = (Node) stack.remove(stack.size() - 1);
            out.add(x.key);
            x = x.right;
        }
        return out;
    }

    public Set keySet() {
        return new ListSet(keys());
    }

    public Collection values() {
        ArrayList v = new ArrayList();
        ArrayList ks = keys();
        for (int i = 0; i < ks.size(); i++) {
            v.add(get(ks.get(i)));
        }
        return v;
    }

    public Set entrySet() {
        ArrayList es = new ArrayList();
        ArrayList ks = keys();
        for (int i = 0; i < ks.size(); i++) {
            Object k = ks.get(i);
            es.add(new SimpleEntry(k, get(k)));
        }
        return new ListSet(es);
    }

    // --- Node locators for the entry-returning navigation methods. ---

    private Node firstNode() {
        Node x = root;
        if (x == null) {
            return null;
        }
        while (x.left != null) {
            x = x.left;
        }
        return x;
    }

    private Node lastNode() {
        Node x = root;
        if (x == null) {
            return null;
        }
        while (x.right != null) {
            x = x.right;
        }
        return x;
    }

    private Node ceilingNode(Object key) {
        Node x = root;
        Node best = null;
        while (x != null) {
            int c = cmp(key, x.key);
            if (c == 0) {
                return x;
            }
            if (c < 0) {
                best = x;
                x = x.left;
            } else {
                x = x.right;
            }
        }
        return best;
    }

    private Node floorNode(Object key) {
        Node x = root;
        Node best = null;
        while (x != null) {
            int c = cmp(key, x.key);
            if (c == 0) {
                return x;
            }
            if (c > 0) {
                best = x;
                x = x.right;
            } else {
                x = x.left;
            }
        }
        return best;
    }

    private Node higherNode(Object key) {
        Node x = root;
        Node best = null;
        while (x != null) {
            int c = cmp(key, x.key);
            if (c < 0) {
                best = x;
                x = x.left;
            } else {
                x = x.right;
            }
        }
        return best;
    }

    private Node lowerNode(Object key) {
        Node x = root;
        Node best = null;
        while (x != null) {
            int c = cmp(key, x.key);
            if (c > 0) {
                best = x;
                x = x.right;
            } else {
                x = x.left;
            }
        }
        return best;
    }

    private static Map.Entry entryOf(Node n) {
        return (n == null) ? null : new SimpleEntry(n.key, n.value);
    }

    public Map.Entry firstEntry() {
        return entryOf(firstNode());
    }

    public Map.Entry lastEntry() {
        return entryOf(lastNode());
    }

    public Map.Entry ceilingEntry(Object key) {
        return entryOf(ceilingNode(key));
    }

    public Map.Entry floorEntry(Object key) {
        return entryOf(floorNode(key));
    }

    public Map.Entry higherEntry(Object key) {
        return entryOf(higherNode(key));
    }

    public Map.Entry lowerEntry(Object key) {
        return entryOf(lowerNode(key));
    }

    public Map.Entry pollFirstEntry() {
        Node n = firstNode();
        if (n == null) {
            return null;
        }
        Map.Entry e = new SimpleEntry(n.key, n.value);
        remove(n.key);
        return e;
    }

    public Map.Entry pollLastEntry() {
        Node n = lastNode();
        if (n == null) {
            return null;
        }
        Map.Entry e = new SimpleEntry(n.key, n.value);
        remove(n.key);
        return e;
    }

    // In-order keys reversed (descending order).
    private ArrayList descendingKeys() {
        ArrayList asc = keys();
        ArrayList out = new ArrayList();
        for (int i = asc.size() - 1; i >= 0; i--) {
            out.add(asc.get(i));
        }
        return out;
    }

    public NavigableSet descendingKeySet() {
        return new NavListSet(descendingKeys());
    }

    public NavigableMap descendingMap() {
        return new DescendingMap(this);
    }

    // Reverses a comparator (or natural ordering when the delegate is null).
    static final class ReverseComparator implements Comparator {
        private final Comparator base;

        ReverseComparator(Comparator base) {
            this.base = base;
        }

        public int compare(Object a, Object b) {
            if (base != null) {
                return base.compare(b, a);
            }
            return ((Comparable) b).compareTo(a);
        }
    }

    // Live descending view backed by the enclosing TreeMap.
    static final class DescendingMap implements NavigableMap {
        private final TreeMap m;

        DescendingMap(TreeMap m) {
            this.m = m;
        }

        public int size() {
            return m.size();
        }

        public boolean isEmpty() {
            return m.isEmpty();
        }

        public Object get(Object key) {
            return m.get(key);
        }

        public Object put(Object key, Object value) {
            return m.put(key, value);
        }

        public Object remove(Object key) {
            return m.remove(key);
        }

        public boolean containsKey(Object key) {
            return m.containsKey(key);
        }

        public boolean containsValue(Object value) {
            return m.containsValue(value);
        }

        public void clear() {
            m.clear();
        }

        public Comparator comparator() {
            return new ReverseComparator(m.comparator());
        }

        public Object firstKey() {
            return m.lastKey();
        }

        public Object lastKey() {
            return m.firstKey();
        }

        public Object ceilingKey(Object key) {
            return m.floorKey(key);
        }

        public Object floorKey(Object key) {
            return m.ceilingKey(key);
        }

        public Object higherKey(Object key) {
            return m.lowerKey(key);
        }

        public Object lowerKey(Object key) {
            return m.higherKey(key);
        }

        public Set keySet() {
            return m.descendingKeySet();
        }

        public Collection values() {
            ArrayList ks = m.descendingKeys();
            ArrayList v = new ArrayList();
            for (int i = 0; i < ks.size(); i++) {
                v.add(m.get(ks.get(i)));
            }
            return v;
        }

        public Set entrySet() {
            ArrayList ks = m.descendingKeys();
            ArrayList es = new ArrayList();
            for (int i = 0; i < ks.size(); i++) {
                Object k = ks.get(i);
                es.add(new SimpleEntry(k, m.get(k)));
            }
            return new ListSet(es);
        }

        public NavigableMap descendingMap() {
            return m;
        }

        public NavigableSet descendingKeySet() {
            return new NavListSet(m.keys());
        }
    }
}
