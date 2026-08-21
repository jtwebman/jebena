package java.util;

// A read-only Set that iterates in the backing list's order (used for the
// ordered keySet/entrySet views of TreeMap/LinkedHashMap).
class ListSet implements Set {
    private final ArrayList list;

    ListSet(ArrayList list) {
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
