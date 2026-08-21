package java.util;

/**
 * Clean-room java.util.PriorityQueue as a binary min-heap in an Object[].
 * Index is 0-based: the children of node i live at 2i+1 and 2i+2, and its
 * parent at (i-1)/2. Ordering follows a supplied Comparator, or the elements'
 * natural ordering (Comparable) when no comparator is given. poll/peek always
 * return the current minimum. The backing array doubles when full; growth and
 * copies use plain loops (no System.arraycopy dependency). The iterator walks
 * the backing array in heap order, which is not sorted -- matching the JDK.
 */
public class PriorityQueue implements Queue {
    private Object[] heap;
    private int size;
    private final Comparator comparator;

    public PriorityQueue() {
        heap = new Object[11];
        size = 0;
        comparator = null;
    }

    public PriorityQueue(int initialCapacity) {
        if (initialCapacity < 1) {
            throw new IllegalArgumentException();
        }
        heap = new Object[initialCapacity];
        size = 0;
        comparator = null;
    }

    public PriorityQueue(Comparator comparator) {
        heap = new Object[11];
        size = 0;
        this.comparator = comparator;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    private int compare(Object a, Object b) {
        if (comparator != null) {
            return comparator.compare(a, b);
        }
        return ((Comparable) a).compareTo(b);
    }

    private void ensure(int min) {
        if (min > heap.length) {
            int nc = heap.length * 2;
            if (nc < min) {
                nc = min;
            }
            Object[] nh = new Object[nc];
            for (int i = 0; i < size; i++) {
                nh[i] = heap[i];
            }
            heap = nh;
        }
    }

    private void siftUp(int i) {
        Object x = heap[i];
        while (i > 0) {
            int parent = (i - 1) / 2;
            if (compare(x, heap[parent]) >= 0) {
                break;
            }
            heap[i] = heap[parent];
            i = parent;
        }
        heap[i] = x;
    }

    private void siftDown(int i) {
        Object x = heap[i];
        int half = size / 2;
        while (i < half) {
            int child = 2 * i + 1;
            int right = child + 1;
            if (right < size && compare(heap[right], heap[child]) < 0) {
                child = right;
            }
            if (compare(x, heap[child]) <= 0) {
                break;
            }
            heap[i] = heap[child];
            i = child;
        }
        heap[i] = x;
    }

    public boolean offer(Object e) {
        if (e == null) {
            throw new NullPointerException();
        }
        ensure(size + 1);
        heap[size] = e;
        siftUp(size);
        size++;
        return true;
    }

    public boolean add(Object e) {
        return offer(e);
    }

    public Object peek() {
        if (size == 0) {
            return null;
        }
        return heap[0];
    }

    public Object element() {
        if (size == 0) {
            throw new NoSuchElementException();
        }
        return heap[0];
    }

    public Object poll() {
        if (size == 0) {
            return null;
        }
        Object result = heap[0];
        size--;
        Object last = heap[size];
        heap[size] = null;
        if (size > 0) {
            heap[0] = last;
            siftDown(0);
        }
        return result;
    }

    public Object remove() {
        if (size == 0) {
            throw new NoSuchElementException();
        }
        return poll();
    }

    public boolean contains(Object o) {
        return indexOf(o) >= 0;
    }

    private int indexOf(Object o) {
        if (o == null) {
            return -1;
        }
        for (int i = 0; i < size; i++) {
            if (o.equals(heap[i])) {
                return i;
            }
        }
        return -1;
    }

    public boolean remove(Object o) {
        int i = indexOf(o);
        if (i < 0) {
            return false;
        }
        size--;
        if (i == size) {
            heap[size] = null;
        } else {
            Object last = heap[size];
            heap[size] = null;
            heap[i] = last;
            siftDown(i);
            if (heap[i] == last) {
                siftUp(i);
            }
        }
        return true;
    }

    public void clear() {
        for (int i = 0; i < size; i++) {
            heap[i] = null;
        }
        size = 0;
    }

    public Iterator iterator() {
        return new Itr();
    }

    private class Itr implements Iterator {
        private int cursor;

        public boolean hasNext() {
            return cursor < size;
        }

        public Object next() {
            if (cursor >= size) {
                throw new NoSuchElementException();
            }
            return heap[cursor++];
        }
    }
}
