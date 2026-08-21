package java.util;

/**
 * Clean-room java.util.LinkedList: a doubly-linked list implementing List and
 * Deque. get(index) walks from the nearer end.
 */
public class LinkedList implements List, Deque {
    private static class Node {
        Object item;
        Node prev;
        Node next;

        Node(Node prev, Object item, Node next) {
            this.prev = prev;
            this.item = item;
            this.next = next;
        }
    }

    private Node first;
    private Node last;
    private int size;

    public LinkedList() {
        first = null;
        last = null;
        size = 0;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void addFirst(Object e) {
        Node f = first;
        Node n = new Node(null, e, f);
        first = n;
        if (f == null) {
            last = n;
        } else {
            f.prev = n;
        }
        size++;
    }

    public void addLast(Object e) {
        Node l = last;
        Node n = new Node(l, e, null);
        last = n;
        if (l == null) {
            first = n;
        } else {
            l.next = n;
        }
        size++;
    }

    public boolean add(Object e) {
        addLast(e);
        return true;
    }

    public void push(Object e) {
        addFirst(e);
    }

    public boolean offer(Object e) {
        addLast(e);
        return true;
    }

    private Node node(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException();
        }
        if (index < (size >> 1)) {
            Node x = first;
            for (int i = 0; i < index; i++) {
                x = x.next;
            }
            return x;
        }
        Node x = last;
        for (int i = size - 1; i > index; i--) {
            x = x.prev;
        }
        return x;
    }

    public Object get(int index) {
        return node(index).item;
    }

    public Object set(int index, Object element) {
        Node x = node(index);
        Object old = x.item;
        x.item = element;
        return old;
    }

    public Object getFirst() {
        if (first == null) {
            throw new NoSuchElementException();
        }
        return first.item;
    }

    public Object getLast() {
        if (last == null) {
            throw new NoSuchElementException();
        }
        return last.item;
    }

    public Object peekFirst() {
        return (first == null) ? null : first.item;
    }

    public Object peekLast() {
        return (last == null) ? null : last.item;
    }

    public Object peek() {
        return peekFirst();
    }

    public Object element() {
        return getFirst();
    }

    private Object unlinkFirst() {
        Node f = first;
        Object item = f.item;
        Node next = f.next;
        first = next;
        if (next == null) {
            last = null;
        } else {
            next.prev = null;
        }
        size--;
        return item;
    }

    private Object unlinkLast() {
        Node l = last;
        Object item = l.item;
        Node prev = l.prev;
        last = prev;
        if (prev == null) {
            first = null;
        } else {
            prev.next = null;
        }
        size--;
        return item;
    }

    public Object removeFirst() {
        if (first == null) {
            throw new NoSuchElementException();
        }
        return unlinkFirst();
    }

    public Object removeLast() {
        if (last == null) {
            throw new NoSuchElementException();
        }
        return unlinkLast();
    }

    public Object pollFirst() {
        return (first == null) ? null : unlinkFirst();
    }

    public Object pollLast() {
        return (last == null) ? null : unlinkLast();
    }

    public Object poll() {
        return pollFirst();
    }

    public Object remove() {
        return removeFirst();
    }

    public Object pop() {
        return removeFirst();
    }

    public int indexOf(Object o) {
        int i = 0;
        for (Node x = first; x != null; x = x.next) {
            if (o == null ? x.item == null : o.equals(x.item)) {
                return i;
            }
            i++;
        }
        return -1;
    }

    public boolean contains(Object o) {
        return indexOf(o) >= 0;
    }

    public Object remove(int index) {
        Node x = node(index);
        Object item = x.item;
        Node prev = x.prev;
        Node next = x.next;
        if (prev == null) {
            first = next;
        } else {
            prev.next = next;
        }
        if (next == null) {
            last = prev;
        } else {
            next.prev = prev;
        }
        size--;
        return item;
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
        first = null;
        last = null;
        size = 0;
    }

    public Iterator iterator() {
        return new LinkedListItr(first);
    }

    public String toString() {
        String s = "[";
        boolean firstItem = true;
        for (Node x = first; x != null; x = x.next) {
            if (!firstItem) {
                s = s + ", ";
            }
            s = s + String.valueOf(x.item);
            firstItem = false;
        }
        return s + "]";
    }

    static class LinkedListItr implements Iterator {
        private Node next;

        LinkedListItr(Node first) {
            this.next = first;
        }

        public boolean hasNext() {
            return next != null;
        }

        public Object next() {
            Object item = next.item;
            next = next.next;
            return item;
        }
    }
}
