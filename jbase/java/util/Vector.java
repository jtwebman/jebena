package java.util;

/**
 * Clean-room java.util.Vector backed by an Object[]. Autoboxed elements are
 * stored as real wrapper instances. Growth doubles the backing array (copied by
 * a plain loop; no System.arraycopy dependency). Jebena is single-threaded, so
 * none of the historical synchronization is present.
 */
public class Vector implements List {
    protected Object[] elementData;
    protected int elementCount;

    public Vector() {
        this(10);
    }

    public Vector(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Illegal Capacity: " + initialCapacity);
        }
        elementData = new Object[initialCapacity];
        elementCount = 0;
    }

    private void ensureCapacity(int min) {
        if (min > elementData.length) {
            int nc = elementData.length * 2;
            if (nc < min) {
                nc = min;
            }
            if (nc < 1) {
                nc = 1;
            }
            Object[] ne = new Object[nc];
            for (int i = 0; i < elementCount; i++) {
                ne[i] = elementData[i];
            }
            elementData = ne;
        }
    }

    public int size() {
        return elementCount;
    }

    public boolean isEmpty() {
        return elementCount == 0;
    }

    public int capacity() {
        return elementData.length;
    }

    public boolean contains(Object o) {
        return indexOf(o) >= 0;
    }

    public int indexOf(Object o) {
        for (int i = 0; i < elementCount; i++) {
            if (o == null ? elementData[i] == null : o.equals(elementData[i])) {
                return i;
            }
        }
        return -1;
    }

    public boolean add(Object e) {
        ensureCapacity(elementCount + 1);
        elementData[elementCount++] = e;
        return true;
    }

    public Object get(int index) {
        if (index < 0 || index >= elementCount) {
            throw new ArrayIndexOutOfBoundsException("Array index out of range: " + index);
        }
        return elementData[index];
    }

    public Object set(int index, Object element) {
        if (index < 0 || index >= elementCount) {
            throw new ArrayIndexOutOfBoundsException("Array index out of range: " + index);
        }
        Object old = elementData[index];
        elementData[index] = element;
        return old;
    }

    public Object remove(int index) {
        if (index < 0 || index >= elementCount) {
            throw new ArrayIndexOutOfBoundsException("Array index out of range: " + index);
        }
        Object old = elementData[index];
        for (int i = index; i < elementCount - 1; i++) {
            elementData[i] = elementData[i + 1];
        }
        elementData[--elementCount] = null;
        return old;
    }

    public boolean remove(Object o) {
        int i = indexOf(o);
        if (i >= 0) {
            remove(i);
            return true;
        }
        return false;
    }

    public void clear() {
        for (int i = 0; i < elementCount; i++) {
            elementData[i] = null;
        }
        elementCount = 0;
    }

    public Iterator iterator() {
        return new Itr();
    }

    // Legacy element-oriented API.

    public void addElement(Object obj) {
        ensureCapacity(elementCount + 1);
        elementData[elementCount++] = obj;
    }

    public Object elementAt(int index) {
        if (index < 0 || index >= elementCount) {
            throw new ArrayIndexOutOfBoundsException("Array index out of range: " + index);
        }
        return elementData[index];
    }

    public Object firstElement() {
        if (elementCount == 0) {
            throw new NoSuchElementException();
        }
        return elementData[0];
    }

    public Object lastElement() {
        if (elementCount == 0) {
            throw new NoSuchElementException();
        }
        return elementData[elementCount - 1];
    }

    public void removeElementAt(int index) {
        if (index < 0 || index >= elementCount) {
            throw new ArrayIndexOutOfBoundsException("Array index out of range: " + index);
        }
        for (int i = index; i < elementCount - 1; i++) {
            elementData[i] = elementData[i + 1];
        }
        elementData[--elementCount] = null;
    }

    public void insertElementAt(Object obj, int index) {
        if (index < 0 || index > elementCount) {
            throw new ArrayIndexOutOfBoundsException("Array index out of range: " + index);
        }
        ensureCapacity(elementCount + 1);
        for (int i = elementCount; i > index; i--) {
            elementData[i] = elementData[i - 1];
        }
        elementData[index] = obj;
        elementCount++;
    }

    public void setSize(int newSize) {
        if (newSize < 0) {
            throw new ArrayIndexOutOfBoundsException("Array index out of range: " + newSize);
        }
        if (newSize > elementCount) {
            ensureCapacity(newSize);
        } else {
            for (int i = newSize; i < elementCount; i++) {
                elementData[i] = null;
            }
        }
        elementCount = newSize;
    }

    public String toString() {
        if (elementCount == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < elementCount; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            Object e = elementData[i];
            sb.append(e == this ? "(this Collection)" : String.valueOf(e));
        }
        sb.append(']');
        return sb.toString();
    }

    private class Itr implements Iterator {
        private int cursor;

        public boolean hasNext() {
            return cursor < elementCount;
        }

        public Object next() {
            if (cursor >= elementCount) {
                throw new NoSuchElementException();
            }
            return elementData[cursor++];
        }
    }
}
