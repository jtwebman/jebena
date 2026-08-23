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

    public boolean addAll(Collection c) {
        boolean changed = false;
        Iterator it = c.iterator();
        while (it.hasNext()) {
            if (add(it.next())) {
                changed = true;
            }
        }
        return changed;
    }

    public boolean containsAll(Collection c) {
        Iterator it = c.iterator();
        while (it.hasNext()) {
            if (!contains(it.next())) {
                return false;
            }
        }
        return true;
    }

    public boolean removeAll(Collection c) {
        boolean changed = false;
        Iterator it = c.iterator();
        while (it.hasNext()) {
            if (remove(it.next())) {
                changed = true;
            }
        }
        return changed;
    }

    public boolean retainAll(Collection c) {
        // Copy keys first (can't structurally modify the backing map while iterating it).
        ArrayList keys = map.keys();
        boolean changed = false;
        for (int i = 0; i < keys.size(); i++) {
            Object k = keys.get(i);
            if (!c.contains(k)) {
                remove(k);
                changed = true;
            }
        }
        return changed;
    }
}
