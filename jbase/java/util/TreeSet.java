package java.util;

public class TreeSet implements Set {
    private final TreeMap map;
    private static final Object PRESENT = new Object();

    public TreeSet() {
        map = new TreeMap();
    }

    public TreeSet(Comparator c) {
        map = new TreeMap(c);
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

    public Object first() {
        return map.firstKey();
    }

    public Object last() {
        return map.lastKey();
    }

    public Object ceiling(Object e) {
        return map.ceilingKey(e);
    }

    public Object floor(Object e) {
        return map.floorKey(e);
    }

    public Object higher(Object e) {
        return map.higherKey(e);
    }

    public Object lower(Object e) {
        return map.lowerKey(e);
    }

    public Iterator iterator() {
        return map.keys().iterator();
    }
}
