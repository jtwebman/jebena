package java.util.concurrent;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Clean-room java.util.concurrent.CopyOnWriteArrayList.
 *
 * <p>All mutators are synchronized and replace the backing array with a fresh
 * copy, so readers observe an immutable snapshot with no locking. Iterators
 * capture the array reference at creation time and never reflect later
 * mutations; their {@code remove} is unsupported.
 */
public class CopyOnWriteArrayList implements List {
    private volatile Object[] array;

    public CopyOnWriteArrayList() {
        array = new Object[0];
    }

    public CopyOnWriteArrayList(Collection c) {
        Object[] a = new Object[c.size()];
        int i = 0;
        for (Object o : c) {
            a[i++] = o;
        }
        if (i != a.length) {
            Object[] t = new Object[i];
            for (int j = 0; j < i; j++) {
                t[j] = a[j];
            }
            a = t;
        }
        array = a;
    }

    private Object[] getArray() {
        return array;
    }

    public int size() {
        return getArray().length;
    }

    public boolean isEmpty() {
        return getArray().length == 0;
    }

    public Object get(int index) {
        Object[] a = getArray();
        if (index < 0 || index >= a.length) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + a.length);
        }
        return a[index];
    }

    private static int indexOfRange(Object o, Object[] a, int from, int to) {
        if (o == null) {
            for (int i = from; i < to; i++) {
                if (a[i] == null) {
                    return i;
                }
            }
        } else {
            for (int i = from; i < to; i++) {
                if (o.equals(a[i])) {
                    return i;
                }
            }
        }
        return -1;
    }

    public int indexOf(Object o) {
        Object[] a = getArray();
        return indexOfRange(o, a, 0, a.length);
    }

    public int lastIndexOf(Object o) {
        Object[] a = getArray();
        if (o == null) {
            for (int i = a.length - 1; i >= 0; i--) {
                if (a[i] == null) {
                    return i;
                }
            }
        } else {
            for (int i = a.length - 1; i >= 0; i--) {
                if (o.equals(a[i])) {
                    return i;
                }
            }
        }
        return -1;
    }

    public boolean contains(Object o) {
        return indexOf(o) >= 0;
    }

    public synchronized boolean add(Object e) {
        Object[] a = getArray();
        int n = a.length;
        Object[] ne = new Object[n + 1];
        for (int i = 0; i < n; i++) {
            ne[i] = a[i];
        }
        ne[n] = e;
        array = ne;
        return true;
    }

    public synchronized void add(int index, Object e) {
        Object[] a = getArray();
        int n = a.length;
        if (index < 0 || index > n) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + n);
        }
        Object[] ne = new Object[n + 1];
        for (int i = 0; i < index; i++) {
            ne[i] = a[i];
        }
        ne[index] = e;
        for (int i = index; i < n; i++) {
            ne[i + 1] = a[i];
        }
        array = ne;
    }

    public synchronized Object set(int index, Object e) {
        Object[] a = getArray();
        int n = a.length;
        if (index < 0 || index >= n) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + n);
        }
        Object old = a[index];
        if (old != e) {
            Object[] ne = new Object[n];
            for (int i = 0; i < n; i++) {
                ne[i] = a[i];
            }
            ne[index] = e;
            array = ne;
        }
        return old;
    }

    public synchronized Object remove(int index) {
        Object[] a = getArray();
        int n = a.length;
        if (index < 0 || index >= n) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + n);
        }
        Object old = a[index];
        Object[] ne = new Object[n - 1];
        for (int i = 0; i < index; i++) {
            ne[i] = a[i];
        }
        for (int i = index + 1; i < n; i++) {
            ne[i - 1] = a[i];
        }
        array = ne;
        return old;
    }

    public synchronized boolean remove(Object o) {
        Object[] a = getArray();
        int n = a.length;
        int idx = indexOfRange(o, a, 0, n);
        if (idx < 0) {
            return false;
        }
        Object[] ne = new Object[n - 1];
        for (int i = 0; i < idx; i++) {
            ne[i] = a[i];
        }
        for (int i = idx + 1; i < n; i++) {
            ne[i - 1] = a[i];
        }
        array = ne;
        return true;
    }

    public synchronized void clear() {
        array = new Object[0];
    }

    public synchronized boolean addIfAbsent(Object e) {
        Object[] a = getArray();
        int n = a.length;
        if (indexOfRange(e, a, 0, n) >= 0) {
            return false;
        }
        Object[] ne = new Object[n + 1];
        for (int i = 0; i < n; i++) {
            ne[i] = a[i];
        }
        ne[n] = e;
        array = ne;
        return true;
    }

    public synchronized int addAllAbsent(Collection c) {
        int added = 0;
        for (Object o : c) {
            Object[] a = getArray();
            int n = a.length;
            if (indexOfRange(o, a, 0, n) >= 0) {
                continue;
            }
            Object[] ne = new Object[n + 1];
            for (int i = 0; i < n; i++) {
                ne[i] = a[i];
            }
            ne[n] = o;
            array = ne;
            added++;
        }
        return added;
    }

    public synchronized boolean addAll(Collection c) {
        int cs = c.size();
        if (cs == 0) {
            return false;
        }
        Object[] a = getArray();
        int n = a.length;
        Object[] ne = new Object[n + cs];
        for (int i = 0; i < n; i++) {
            ne[i] = a[i];
        }
        int j = n;
        for (Object o : c) {
            ne[j++] = o;
        }
        if (j != ne.length) {
            Object[] t = new Object[j];
            for (int i = 0; i < j; i++) {
                t[i] = ne[i];
            }
            ne = t;
        }
        array = ne;
        return true;
    }

    public Iterator iterator() {
        return new COWIterator(getArray());
    }

    public String toString() {
        Object[] a = getArray();
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < a.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            Object v = a[i];
            sb.append(v == this ? "(this Collection)" : String.valueOf(v));
        }
        sb.append(']');
        return sb.toString();
    }

    private static final class COWIterator implements Iterator {
        private final Object[] snapshot;
        private int cursor;

        COWIterator(Object[] snapshot) {
            this.snapshot = snapshot;
            this.cursor = 0;
        }

        public boolean hasNext() {
            return cursor < snapshot.length;
        }

        public Object next() {
            if (cursor >= snapshot.length) {
                throw new NoSuchElementException();
            }
            return snapshot[cursor++];
        }

        public void remove() {
            throw new UnsupportedOperationException("remove");
        }
    }
}
