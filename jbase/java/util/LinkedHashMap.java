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
    private float loadFactor;

    // When true, get() moves the accessed entry to the end of the order list
    // (least-recently used first), enabling LRU behavior. When false, the list
    // stays in insertion order.
    private boolean accessOrder;

    // Head (oldest / least-recently-used) and tail (newest / most-recently-used)
    // of the order list.
    private Node head;
    private Node tail;

    private static final int DEFAULT_CAPACITY = 16;
    private static final float LOAD_FACTOR = 0.75f;

    public LinkedHashMap() {
        table = new Node[DEFAULT_CAPACITY];
        loadFactor = LOAD_FACTOR;
        threshold = (int) (DEFAULT_CAPACITY * LOAD_FACTOR);
        size = 0;
        head = null;
        tail = null;
        accessOrder = false;
    }

    public LinkedHashMap(int initialCapacity) {
        this(initialCapacity, LOAD_FACTOR, false);
    }

    public LinkedHashMap(int initialCapacity, float loadFactor) {
        this(initialCapacity, loadFactor, false);
    }

    public LinkedHashMap(int initialCapacity, float loadFactor, boolean accessOrder) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Illegal initial capacity: " + initialCapacity);
        }
        if (loadFactor <= 0 || loadFactor != loadFactor) {
            throw new IllegalArgumentException("Illegal load factor: " + loadFactor);
        }
        int cap = tableSizeFor(initialCapacity < 1 ? 1 : initialCapacity);
        this.table = new Node[cap];
        this.loadFactor = loadFactor;
        this.threshold = (int) (cap * loadFactor);
        this.size = 0;
        this.head = null;
        this.tail = null;
        this.accessOrder = accessOrder;
    }

    private static int tableSizeFor(int c) {
        int n = 1;
        while (n < c) {
            n <<= 1;
        }
        return n;
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
        if (e == null) {
            return null;
        }
        if (accessOrder) {
            moveToTail(e);
        }
        return e.value;
    }

    private void moveToTail(Node node) {
        if (tail == node) {
            return;
        }
        unlink(node);
        linkTail(node);
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
            if (accessOrder) {
                moveToTail(e);
            }
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
        afterNodeInsertion();
        return null;
    }

    /**
     * Called after inserting a new entry. If the map wants to evict its eldest
     * (least-recently-used, in access order; oldest inserted otherwise) entry,
     * removeEldestEntry returns true and that entry is removed. Subclasses
     * override removeEldestEntry to build a size-bounded LRU cache.
     */
    private void afterNodeInsertion() {
        Node first = head;
        if (first != null && removeEldestEntry(new SimpleEntry(first.key, first.value))) {
            remove(first.key);
        }
    }

    protected boolean removeEldestEntry(Map.Entry eldest) {
        return false;
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
        threshold = (int) (newCap * loadFactor);
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
