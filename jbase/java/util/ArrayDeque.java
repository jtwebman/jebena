package java.util;

/**
 * Clean-room java.util.ArrayDeque: a resizable-array (circular buffer) Deque.
 * Elements live in the ring buffer between head (inclusive) and tail
 * (exclusive). The buffer is kept with at least one empty slot so head == tail
 * unambiguously means empty; when a push would fill the last slot the buffer is
 * doubled. Null elements are not permitted.
 */
public class ArrayDeque implements Deque {
    private Object[] elements;
    private int head;
    private int tail;

    public ArrayDeque() {
        elements = new Object[16];
        head = 0;
        tail = 0;
    }

    private void doubleCapacity() {
        // Called only when the ring buffer is full (head == tail after an add), so
        // it holds exactly oldCap elements. size() reads 0 here (head == tail looks
        // empty), so copy oldCap elements, not size(), or the deque loses them all.
        int oldCap = elements.length;
        int newCap = oldCap << 1;
        if (newCap <= 0) {
            newCap = oldCap + 1;
        }
        Object[] a = new Object[newCap];
        int idx = head;
        for (int i = 0; i < oldCap; i++) {
            a[i] = elements[idx];
            idx = (idx + 1) % oldCap;
        }
        elements = a;
        head = 0;
        tail = oldCap;
    }

    public int size() {
        int diff = tail - head;
        if (diff < 0) {
            diff += elements.length;
        }
        return diff;
    }

    public boolean isEmpty() {
        return head == tail;
    }

    public void addFirst(Object e) {
        if (e == null) {
            throw new NullPointerException();
        }
        head = (head - 1 + elements.length) % elements.length;
        elements[head] = e;
        if (head == tail) {
            doubleCapacity();
        }
    }

    public void addLast(Object e) {
        if (e == null) {
            throw new NullPointerException();
        }
        elements[tail] = e;
        tail = (tail + 1) % elements.length;
        if (head == tail) {
            doubleCapacity();
        }
    }

    public boolean add(Object e) {
        addLast(e);
        return true;
    }

    public boolean offer(Object e) {
        return offerLast(e);
    }

    public boolean offerFirst(Object e) {
        addFirst(e);
        return true;
    }

    public boolean offerLast(Object e) {
        addLast(e);
        return true;
    }

    public void push(Object e) {
        addFirst(e);
    }

    public Object pollFirst() {
        if (head == tail) {
            return null;
        }
        Object result = elements[head];
        elements[head] = null;
        head = (head + 1) % elements.length;
        return result;
    }

    public Object pollLast() {
        if (head == tail) {
            return null;
        }
        tail = (tail - 1 + elements.length) % elements.length;
        Object result = elements[tail];
        elements[tail] = null;
        return result;
    }

    public Object poll() {
        return pollFirst();
    }

    public Object pop() {
        return removeFirst();
    }

    public Object removeFirst() {
        Object result = pollFirst();
        if (result == null) {
            throw new NoSuchElementException();
        }
        return result;
    }

    public Object removeLast() {
        Object result = pollLast();
        if (result == null) {
            throw new NoSuchElementException();
        }
        return result;
    }

    public Object remove() {
        return removeFirst();
    }

    public Object peekFirst() {
        if (head == tail) {
            return null;
        }
        return elements[head];
    }

    public Object peekLast() {
        if (head == tail) {
            return null;
        }
        return elements[(tail - 1 + elements.length) % elements.length];
    }

    public Object peek() {
        return peekFirst();
    }

    public Object getFirst() {
        Object result = peekFirst();
        if (result == null) {
            throw new NoSuchElementException();
        }
        return result;
    }

    public Object getLast() {
        Object result = peekLast();
        if (result == null) {
            throw new NoSuchElementException();
        }
        return result;
    }

    public Object element() {
        return getFirst();
    }

    public boolean contains(Object o) {
        if (o == null) {
            return false;
        }
        int idx = head;
        while (idx != tail) {
            if (o.equals(elements[idx])) {
                return true;
            }
            idx = (idx + 1) % elements.length;
        }
        return false;
    }

    /** Removes the element at the given buffer index, closing the gap. */
    private void deleteAt(int i) {
        int len = elements.length;
        int front = (i - head + len) % len;
        int back = (tail - i - 1 + len) % len;
        if (front <= back) {
            // Shift the front segment forward.
            int idx = i;
            while (idx != head) {
                int prev = (idx - 1 + len) % len;
                elements[idx] = elements[prev];
                idx = prev;
            }
            elements[head] = null;
            head = (head + 1) % len;
        } else {
            // Shift the back segment backward.
            int idx = i;
            int next = (idx + 1) % len;
            while (next != tail) {
                elements[idx] = elements[next];
                idx = next;
                next = (next + 1) % len;
            }
            elements[idx] = null;
            tail = idx;
        }
    }

    public boolean remove(Object o) {
        if (o == null) {
            return false;
        }
        int idx = head;
        while (idx != tail) {
            if (o.equals(elements[idx])) {
                deleteAt(idx);
                return true;
            }
            idx = (idx + 1) % elements.length;
        }
        return false;
    }

    public void clear() {
        int idx = head;
        while (idx != tail) {
            elements[idx] = null;
            idx = (idx + 1) % elements.length;
        }
        head = 0;
        tail = 0;
    }

    public Iterator iterator() {
        return new Itr();
    }

    private class Itr implements Iterator {
        private int cursor = head;

        public boolean hasNext() {
            return cursor != tail;
        }

        public Object next() {
            if (cursor == tail) {
                throw new NoSuchElementException();
            }
            Object result = elements[cursor];
            cursor = (cursor + 1) % elements.length;
            return result;
        }
    }

    public String toString() {
        if (head == tail) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        int idx = head;
        boolean first = true;
        while (idx != tail) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(String.valueOf(elements[idx]));
            first = false;
            idx = (idx + 1) % elements.length;
        }
        sb.append(']');
        return sb.toString();
    }
}
