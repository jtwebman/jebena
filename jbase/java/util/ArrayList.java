package java.util;

/**
 * Clean-room java.util.ArrayList backed by an Object[]. Autoboxed elements are
 * stored as real wrapper instances. Growth doubles the backing array (copied by
 * a plain loop; no System.arraycopy dependency).
 */
public class ArrayList implements List {
    private Object[] elements;
    private int size;

    public ArrayList() {
        elements = new Object[10];
        size = 0;
    }

    public ArrayList(int capacity) {
        elements = new Object[capacity < 1 ? 1 : capacity];
        size = 0;
    }

    public ArrayList(Collection c) {
        int n = c.size();
        elements = new Object[n < 1 ? 1 : n];
        size = 0;
        Iterator it = c.iterator();
        while (it.hasNext()) {
            add(it.next());
        }
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    private void ensure(int min) {
        if (min > elements.length) {
            int nc = elements.length * 2;
            if (nc < min) {
                nc = min;
            }
            Object[] ne = new Object[nc];
            for (int i = 0; i < size; i++) {
                ne[i] = elements[i];
            }
            elements = ne;
        }
    }

    public boolean add(Object e) {
        ensure(size + 1);
        elements[size++] = e;
        return true;
    }

    public Object get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException();
        }
        return elements[index];
    }

    public Object set(int index, Object e) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException();
        }
        Object old = elements[index];
        elements[index] = e;
        return old;
    }

    public int indexOf(Object o) {
        for (int i = 0; i < size; i++) {
            if (o == null ? elements[i] == null : o.equals(elements[i])) {
                return i;
            }
        }
        return -1;
    }

    public boolean contains(Object o) {
        return indexOf(o) >= 0;
    }

    public Object remove(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException();
        }
        Object old = elements[index];
        for (int i = index; i < size - 1; i++) {
            elements[i] = elements[i + 1];
        }
        elements[--size] = null;
        return old;
    }

    public boolean remove(Object o) {
        int i = indexOf(o);
        if (i < 0) {
            return false;
        }
        remove(i);
        return true;
    }

    public void clear() {
        for (int i = 0; i < size; i++) {
            elements[i] = null;
        }
        size = 0;
    }

    public Object[] toArray() {
        Object[] r = new Object[size];
        for (int i = 0; i < size; i++) {
            r[i] = elements[i];
        }
        return r;
    }

    public java.util.Iterator iterator() {
        return new ArrayListItr(this);
    }

    public String toString() {
        String s = "[";
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                s = s + ", ";
            }
            s = s + String.valueOf(elements[i]);
        }
        return s + "]";
    }
}
