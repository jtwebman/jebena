package java.util;

// A read-only NavigableSet that iterates in the backing list's order (used for
// the descending key-set views of TreeMap). Mirrors ListSet but carries the
// NavigableSet static type so it matches the JDK method descriptors.
class NavListSet implements NavigableSet {
    private final ArrayList list;

    NavListSet(ArrayList list) {
        this.list = list;
    }

    public int size() {
        return list.size();
    }

    public boolean isEmpty() {
        return list.isEmpty();
    }

    public boolean contains(Object o) {
        return list.contains(o);
    }

    public boolean add(Object e) {
        throw new UnsupportedOperationException();
    }

    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    public void clear() {
        throw new UnsupportedOperationException();
    }

    public Iterator iterator() {
        return list.iterator();
    }
}
