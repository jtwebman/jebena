package java.util;

public class HashSet implements Set {
    private final HashMap map;
    private static final Object PRESENT = new Object();

    public HashSet() {
        map = new HashMap();
    }

    public boolean add(Object e) {
        return map.put(e, PRESENT) == null;
    }

    public boolean contains(Object o) {
        return map.containsKey(o);
    }

    public boolean remove(Object o) {
        return map.remove(o) != null;
    }

    public int size() {
        return map.size();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public void clear() {
        map.clear();
    }

    public Iterator iterator() {
        return map.keys().iterator();
    }
}
