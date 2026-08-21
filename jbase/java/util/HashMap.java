package java.util;

/**
 * Clean-room java.util.HashMap: a power-of-two bucket array of singly-linked
 * Node chains, hash spread h ^ (h>>>16), resize (double) at load factor 0.75,
 * using key.hashCode()/equals(). No tree bins.
 */
public class HashMap implements Map {
    static class Node {
        final int hash;
        final Object key;
        Object value;
        Node next;

        Node(int hash, Object key, Object value, Node next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }

    private Node[] table;
    private int size;
    private int threshold;

    private static final int DEFAULT_CAPACITY = 16;
    private static final float LOAD_FACTOR = 0.75f;

    public HashMap() {
        table = new Node[DEFAULT_CAPACITY];
        threshold = (int) (DEFAULT_CAPACITY * LOAD_FACTOR);
        size = 0;
    }

    static int hash(Object key) {
        if (key == null) {
            return 0;
        }
        int h = key.hashCode();
        return h ^ (h >>> 16);
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    private Node find(Object key, int h) {
        int i = h & (table.length - 1);
        for (Node e = table[i]; e != null; e = e.next) {
            if (e.hash == h && (e.key == key || (key != null && key.equals(e.key)))) {
                return e;
            }
        }
        return null;
    }

    public Object get(Object key) {
        Node e = find(key, hash(key));
        return (e == null) ? null : e.value;
    }

    public boolean containsKey(Object key) {
        return find(key, hash(key)) != null;
    }

    public Object put(Object key, Object value) {
        int h = hash(key);
        Node e = find(key, h);
        if (e != null) {
            Object old = e.value;
            e.value = value;
            return old;
        }
        int i = h & (table.length - 1);
        table[i] = new Node(h, key, value, table[i]);
        size++;
        if (size > threshold) {
            resize();
        }
        return null;
    }

    private void resize() {
        Node[] old = table;
        int newCap = old.length * 2;
        Node[] newTab = new Node[newCap];
        for (int j = 0; j < old.length; j++) {
            Node e = old[j];
            while (e != null) {
                Node next = e.next;
                int i = e.hash & (newCap - 1);
                e.next = newTab[i];
                newTab[i] = e;
                e = next;
            }
        }
        table = newTab;
        threshold = (int) (newCap * LOAD_FACTOR);
    }

    public Object remove(Object key) {
        int h = hash(key);
        int i = h & (table.length - 1);
        Node prev = null;
        for (Node e = table[i]; e != null; e = e.next) {
            if (e.hash == h && (e.key == key || (key != null && key.equals(e.key)))) {
                if (prev == null) {
                    table[i] = e.next;
                } else {
                    prev.next = e.next;
                }
                size--;
                return e.value;
            }
            prev = e;
        }
        return null;
    }

    public boolean containsValue(Object value) {
        for (int i = 0; i < table.length; i++) {
            for (Node e = table[i]; e != null; e = e.next) {
                if (value == null ? e.value == null : value.equals(e.value)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void clear() {
        for (int i = 0; i < table.length; i++) {
            table[i] = null;
        }
        size = 0;
    }

    // Order-independent key iteration helper (as a fresh ArrayList).
    public ArrayList keys() {
        ArrayList out = new ArrayList();
        for (int i = 0; i < table.length; i++) {
            for (Node e = table[i]; e != null; e = e.next) {
                out.add(e.key);
            }
        }
        return out;
    }

    public Set keySet() {
        HashSet s = new HashSet();
        for (int i = 0; i < table.length; i++) {
            for (Node e = table[i]; e != null; e = e.next) {
                s.add(e.key);
            }
        }
        return s;
    }

    public Collection values() {
        ArrayList v = new ArrayList();
        for (int i = 0; i < table.length; i++) {
            for (Node e = table[i]; e != null; e = e.next) {
                v.add(e.value);
            }
        }
        return v;
    }

    public Set entrySet() {
        HashSet s = new HashSet();
        for (int i = 0; i < table.length; i++) {
            for (Node e = table[i]; e != null; e = e.next) {
                s.add(new SimpleEntry(e.key, e.value));
            }
        }
        return s;
    }
}
