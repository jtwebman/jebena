package java.util.concurrent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Clean-room java.util.concurrent.ConcurrentHashMap.
 *
 * <p>Jebena is currently single-threaded, so this makes no attempt at real
 * concurrency, lock striping, or the memory-visibility guarantees of the JDK
 * implementation. It aims only to reproduce ConcurrentHashMap's <em>observable</em>
 * single-thread behavior exactly, so that a driver differentially tested against the
 * real JDK produces identical results.
 *
 * <p>Internals mirror {@link java.util.HashMap}: a power-of-two bucket array of
 * singly-linked {@code Node} chains, hash spread {@code h ^ (h >>> 16)}, doubling
 * resize at load factor 0.75, using {@code key.hashCode()}/{@code equals}. Iteration
 * order is unspecified (as in the real class).
 *
 * <p>The behavioral contract that distinguishes CHM from HashMap and is reproduced
 * here precisely: <strong>null keys and null values are forbidden</strong>. Every
 * key-taking method rejects a null key with {@link NullPointerException}; every
 * value-storing method rejects a null value the same way; and the functional methods
 * (computeIfAbsent/computeIfPresent/compute/merge) treat a null result as "remove /
 * do not store" rather than storing null.
 */
public class ConcurrentHashMap implements ConcurrentMap {

    static final class Node implements Map.Entry {
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

        public Object getKey() {
            return key;
        }

        public Object getValue() {
            return value;
        }

        public int hashCode() {
            return key.hashCode() ^ value.hashCode();
        }

        public String toString() {
            return key + "=" + value;
        }

        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (o instanceof Map.Entry) {
                Map.Entry e = (Map.Entry) o;
                return Objects.equals(key, e.getKey())
                        && Objects.equals(value, e.getValue());
            }
            return false;
        }
    }

    private Node[] table;
    private int size;
    private int threshold;

    private static final int DEFAULT_CAPACITY = 16;
    private static final int MAXIMUM_CAPACITY = 1 << 30;
    private static final float LOAD_FACTOR = 0.75f;

    public ConcurrentHashMap() {
        this.table = new Node[DEFAULT_CAPACITY];
        this.threshold = (int) (DEFAULT_CAPACITY * LOAD_FACTOR);
        this.size = 0;
    }

    public ConcurrentHashMap(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException();
        }
        int cap = tableSizeFor(initialCapacity < DEFAULT_CAPACITY
                ? DEFAULT_CAPACITY : initialCapacity);
        this.table = new Node[cap];
        this.threshold = (int) (cap * LOAD_FACTOR);
        this.size = 0;
    }

    public ConcurrentHashMap(Map m) {
        this();
        putAll(m);
    }

    private static int tableSizeFor(int c) {
        int n = 1;
        while (n < c && n < MAXIMUM_CAPACITY) {
            n <<= 1;
        }
        return n;
    }

    static int spread(Object key) {
        int h = key.hashCode();
        return h ^ (h >>> 16);
    }

    private Node find(Object key, int h) {
        int i = h & (table.length - 1);
        for (Node e = table[i]; e != null; e = e.next) {
            if (e.hash == h && (e.key == key || key.equals(e.key))) {
                return e;
            }
        }
        return null;
    }

    private void addNode(int h, Object key, Object value) {
        int i = h & (table.length - 1);
        table[i] = new Node(h, key, value, table[i]);
        size++;
        if (size > threshold) {
            resize();
        }
    }

    private void resize() {
        Node[] old = table;
        int newCap = old.length * 2;
        if (newCap <= 0) {
            return;
        }
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

    private Object removeNode(Object key, int h) {
        int i = h & (table.length - 1);
        Node prev = null;
        for (Node e = table[i]; e != null; e = e.next) {
            if (e.hash == h && (e.key == key || key.equals(e.key))) {
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

    public synchronized int size() {
        return size;
    }

    public synchronized boolean isEmpty() {
        return size == 0;
    }

    public synchronized Object get(Object key) {
        if (key == null) {
            throw new NullPointerException();
        }
        Node e = find(key, spread(key));
        return (e == null) ? null : e.value;
    }

    public synchronized boolean containsKey(Object key) {
        if (key == null) {
            throw new NullPointerException();
        }
        return find(key, spread(key)) != null;
    }

    public synchronized boolean containsValue(Object value) {
        if (value == null) {
            throw new NullPointerException();
        }
        for (int i = 0; i < table.length; i++) {
            for (Node e = table[i]; e != null; e = e.next) {
                if (value.equals(e.value)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Object putVal(Object key, Object value, boolean onlyIfAbsent) {
        if (key == null || value == null) {
            throw new NullPointerException();
        }
        int h = spread(key);
        Node e = find(key, h);
        if (e != null) {
            Object old = e.value;
            if (!onlyIfAbsent) {
                e.value = value;
            }
            return old;
        }
        addNode(h, key, value);
        return null;
    }

    public synchronized Object put(Object key, Object value) {
        return putVal(key, value, false);
    }

    public synchronized Object putIfAbsent(Object key, Object value) {
        return putVal(key, value, true);
    }

    public synchronized void putAll(Map m) {
        Iterator it = m.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry e = (Map.Entry) it.next();
            put(e.getKey(), e.getValue());
        }
    }

    public synchronized Object remove(Object key) {
        if (key == null) {
            throw new NullPointerException();
        }
        return removeNode(key, spread(key));
    }

    public synchronized boolean remove(Object key, Object value) {
        if (key == null) {
            throw new NullPointerException();
        }
        if (value == null) {
            return false;
        }
        int h = spread(key);
        Node e = find(key, h);
        if (e != null && value.equals(e.value)) {
            removeNode(key, h);
            return true;
        }
        return false;
    }

    public synchronized Object replace(Object key, Object value) {
        if (key == null || value == null) {
            throw new NullPointerException();
        }
        Node e = find(key, spread(key));
        if (e != null) {
            Object old = e.value;
            e.value = value;
            return old;
        }
        return null;
    }

    public synchronized boolean replace(Object key, Object oldValue, Object newValue) {
        if (key == null || oldValue == null || newValue == null) {
            throw new NullPointerException();
        }
        Node e = find(key, spread(key));
        if (e != null && oldValue.equals(e.value)) {
            e.value = newValue;
            return true;
        }
        return false;
    }

    public synchronized Object getOrDefault(Object key, Object defaultValue) {
        if (key == null) {
            throw new NullPointerException();
        }
        Node e = find(key, spread(key));
        return (e == null) ? defaultValue : e.value;
    }

    public synchronized Object computeIfAbsent(Object key, Function mappingFunction) {
        if (key == null || mappingFunction == null) {
            throw new NullPointerException();
        }
        int h = spread(key);
        Node e = find(key, h);
        if (e != null) {
            return e.value;
        }
        Object nv = mappingFunction.apply(key);
        if (nv == null) {
            return null;
        }
        addNode(h, key, nv);
        return nv;
    }

    public synchronized Object computeIfPresent(Object key, BiFunction remappingFunction) {
        if (key == null || remappingFunction == null) {
            throw new NullPointerException();
        }
        int h = spread(key);
        Node e = find(key, h);
        if (e == null) {
            return null;
        }
        Object nv = remappingFunction.apply(key, e.value);
        if (nv != null) {
            e.value = nv;
            return nv;
        }
        removeNode(key, h);
        return null;
    }

    public synchronized Object compute(Object key, BiFunction remappingFunction) {
        if (key == null || remappingFunction == null) {
            throw new NullPointerException();
        }
        int h = spread(key);
        Node e = find(key, h);
        Object oldVal = (e == null) ? null : e.value;
        Object nv = remappingFunction.apply(key, oldVal);
        if (nv == null) {
            if (e != null) {
                removeNode(key, h);
            }
            return null;
        }
        if (e != null) {
            e.value = nv;
        } else {
            addNode(h, key, nv);
        }
        return nv;
    }

    public synchronized Object merge(Object key, Object value, BiFunction remappingFunction) {
        if (key == null || value == null || remappingFunction == null) {
            throw new NullPointerException();
        }
        int h = spread(key);
        Node e = find(key, h);
        if (e == null) {
            addNode(h, key, value);
            return value;
        }
        Object nv = remappingFunction.apply(e.value, value);
        if (nv == null) {
            removeNode(key, h);
            return null;
        }
        e.value = nv;
        return nv;
    }

    public synchronized void forEach(BiConsumer action) {
        if (action == null) {
            throw new NullPointerException();
        }
        for (int i = 0; i < table.length; i++) {
            for (Node e = table[i]; e != null; e = e.next) {
                action.accept(e.key, e.value);
            }
        }
    }

    public synchronized void clear() {
        for (int i = 0; i < table.length; i++) {
            table[i] = null;
        }
        size = 0;
    }

    public synchronized Set keySet() {
        HashSet s = new HashSet();
        for (int i = 0; i < table.length; i++) {
            for (Node e = table[i]; e != null; e = e.next) {
                s.add(e.key);
            }
        }
        return s;
    }

    public synchronized Collection values() {
        ArrayList v = new ArrayList();
        for (int i = 0; i < table.length; i++) {
            for (Node e = table[i]; e != null; e = e.next) {
                v.add(e.value);
            }
        }
        return v;
    }

    public synchronized Set entrySet() {
        HashSet s = new HashSet();
        for (int i = 0; i < table.length; i++) {
            for (Node e = table[i]; e != null; e = e.next) {
                s.add(new Node(e.hash, e.key, e.value, null));
            }
        }
        return s;
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Map)) {
            return false;
        }
        Map m = (Map) o;
        if (m.size() != size) {
            return false;
        }
        try {
            for (int i = 0; i < table.length; i++) {
                for (Node e = table[i]; e != null; e = e.next) {
                    Object v = m.get(e.key);
                    if (v == null || !v.equals(e.value)) {
                        return false;
                    }
                }
            }
        } catch (ClassCastException ex) {
            return false;
        } catch (NullPointerException ex) {
            return false;
        }
        return true;
    }

    public int hashCode() {
        int h = 0;
        for (int i = 0; i < table.length; i++) {
            for (Node e = table[i]; e != null; e = e.next) {
                h += e.key.hashCode() ^ e.value.hashCode();
            }
        }
        return h;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (int i = 0; i < table.length; i++) {
            for (Node e = table[i]; e != null; e = e.next) {
                if (!first) {
                    sb.append(',').append(' ');
                }
                first = false;
                Object k = e.key;
                Object v = e.value;
                sb.append(k == this ? "(this Map)" : k);
                sb.append('=');
                sb.append(v == this ? "(this Map)" : v);
            }
        }
        sb.append('}');
        return sb.toString();
    }
}
