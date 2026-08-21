package java.util;

/**
 * Clean-room java.util.LinkedHashMap: a standalone power-of-two bucket array of
 * singly-linked Node chains (hash spread h ^ (h>>>16), resize/double at load
 * factor 0.75, using key.hashCode()/equals()) that ALSO threads every entry
 * through a doubly-linked list in INSERTION order. keySet(), values() and
 * entrySet() iterate in that insertion order. Re-putting an existing key
 * updates the value but does not change its position in the order.
 */
public class LinkedHashMap implements Map {
    static class Node {
        final int hash;
        final Object key;
        Object value;
        Node next;    // bucket chain
        Node before;  // insertion-order predecessor
        Node after;   // insertion-order successor

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

    // Head (oldest) and tail (newest) of the insertion-order list.
    private Node head;
    private Node tail;

    private static final int DEFAULT_CAPACITY = 16;
    private static final float LOAD_FACTOR = 0.75f;

    public LinkedHashMap() {
        table = new Node[DEFAULT_CAPACITY];
        threshold = (int) (DEFAULT_CAPACITY * LOAD_FACTOR);
        size = 0;
        head = null;
        tail = null;
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
        Node node = new Node(h, key, value, table[i]);
        table[i] = node;
        linkTail(node);
        size++;
        if (size > threshold) {
            resize();
        }
        return null;
    }

    private void linkTail(Node node) {
        Node last = tail;
        tail = node;
        if (last == null) {
            head = node;
        } else {
            node.before = last;
            last.after = node;
        }
    }

    private void unlink(Node node) {
        Node b = node.before;
        Node a = node.after;
        if (b == null) {
            head = a;
        } else {
            b.after = a;
        }
        if (a == null) {
            tail = b;
        } else {
            a.before = b;
        }
        node.before = null;
        node.after = null;
    }

    private void resize() {
        Node[] oldTable = table;
        int newCap = oldTable.length * 2;
        Node[] newTable = new Node[newCap];
        // Rehash each bucket chain into the larger table. Insertion-order links
        // (before/after) are untouched, so ordering is preserved.
        for (int j = 0; j < oldTable.length; j++) {
            Node e = oldTable[j];
            while (e != null) {
                Node nextChain = e.next;
                int i = e.hash & (newCap - 1);
                e.next = newTable[i];
                newTable[i] = e;
                e = nextChain;
            }
        }
        table = newTable;
        threshold = (int) (newCap * LOAD_FACTOR);
    }

    public Object remove(Object key) {
        int h = hash(key);
        int i = h & (table.length - 1);
        Node prev = null;
        for (Node e = table[i]; e != null; prev = e, e = e.next) {
            if (e.hash == h && (e.key == key || (key != null && key.equals(e.key)))) {
                if (prev == null) {
                    table[i] = e.next;
                } else {
                    prev.next = e.next;
                }
                unlink(e);
                size--;
                return e.value;
            }
        }
        return null;
    }

    public boolean containsValue(Object value) {
        for (Node e = head; e != null; e = e.after) {
            if (e.value == value || (value != null && value.equals(e.value))) {
                return true;
            }
        }
        return false;
    }

    public void clear() {
        for (int i = 0; i < table.length; i++) {
            table[i] = null;
        }
        head = null;
        tail = null;
        size = 0;
    }

    public Set keySet() {
        ArrayList list = new ArrayList();
        for (Node e = head; e != null; e = e.after) {
            list.add(e.key);
        }
        return new ListSet(list);
    }

    public Collection values() {
        ArrayList list = new ArrayList();
        for (Node e = head; e != null; e = e.after) {
            list.add(e.value);
        }
        return list;
    }

    public Set entrySet() {
        ArrayList list = new ArrayList();
        for (Node e = head; e != null; e = e.after) {
            list.add(new SimpleEntry(e.key, e.value));
        }
        return new ListSet(list);
    }
}
