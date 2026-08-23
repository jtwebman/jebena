package java.util.concurrent;

import java.util.Deque;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.ArrayList;

// Clean-room ConcurrentLinkedDeque: an unbounded thread-safe deque backed by a
// doubly linked list with head/tail node references. jbase uses coarse-grained
// synchronization (every mutator/accessor is synchronized) rather than the
// lock-free CAS design of the JDK; the externally observable behavior (FIFO/LIFO
// ordering at both ends, null-rejection, poll/peek returning null when empty) is
// identical.
public class ConcurrentLinkedDeque implements Deque {

    private static final class Node {
        Object item;
        Node prev;
        Node next;

        Node(Object item) {
            this.item = item;
        }
    }

    private Node head;
    private Node tail;
    private int count;

    public ConcurrentLinkedDeque() {
        head = null;
        tail = null;
        count = 0;
    }

    public ConcurrentLinkedDeque(Collection c) {
        head = null;
        tail = null;
        count = 0;
        if (c == null) throw new NullPointerException();
        Iterator it = c.iterator();
        while (it.hasNext()) {
            offerLast(it.next());
        }
    }

    public synchronized void addFirst(Object e) {
        if (e == null) throw new NullPointerException();
        Node n = new Node(e);
        if (head == null) {
            head = n;
            tail = n;
        } else {
            n.next = head;
            head.prev = n;
            head = n;
        }
        count++;
    }

    public synchronized void addLast(Object e) {
        if (e == null) throw new NullPointerException();
        Node n = new Node(e);
        if (tail == null) {
            head = n;
            tail = n;
        } else {
            n.prev = tail;
            tail.next = n;
            tail = n;
        }
        count++;
    }

    public synchronized boolean offerFirst(Object e) {
        addFirst(e);
        return true;
    }

    public synchronized boolean offerLast(Object e) {
        addLast(e);
        return true;
    }

    public synchronized boolean offer(Object e) {
        return offerLast(e);
    }

    public synchronized boolean add(Object e) {
        addLast(e);
        return true;
    }

    public synchronized void push(Object e) {
        addFirst(e);
    }

    public synchronized Object pollFirst() {
        if (head == null) {
            return null;
        }
        Node n = head;
        Object item = n.item;
        head = n.next;
        if (head == null) {
            tail = null;
        } else {
            head.prev = null;
        }
        n.next = null;
        n.item = null;
        count--;
        return item;
    }

    public synchronized Object pollLast() {
        if (tail == null) {
            return null;
        }
        Node n = tail;
        Object item = n.item;
        tail = n.prev;
        if (tail == null) {
            head = null;
        } else {
            tail.next = null;
        }
        n.prev = null;
        n.item = null;
        count--;
        return item;
    }

    public synchronized Object poll() {
        return pollFirst();
    }

    public synchronized Object pop() {
        return removeFirst();
    }

    public synchronized Object removeFirst() {
        Object x = pollFirst();
        if (x == null) {
            throw new NoSuchElementException();
        }
        return x;
    }

    public synchronized Object removeLast() {
        Object x = pollLast();
        if (x == null) {
            throw new NoSuchElementException();
        }
        return x;
    }

    public synchronized Object remove() {
        return removeFirst();
    }

    public synchronized Object peekFirst() {
        return head == null ? null : head.item;
    }

    public synchronized Object peekLast() {
        return tail == null ? null : tail.item;
    }

    public synchronized Object peek() {
        return peekFirst();
    }

    public synchronized Object getFirst() {
        Object x = peekFirst();
        if (x == null) {
            throw new NoSuchElementException();
        }
        return x;
    }

    public synchronized Object getLast() {
        Object x = peekLast();
        if (x == null) {
            throw new NoSuchElementException();
        }
        return x;
    }

    public synchronized Object element() {
        return getFirst();
    }

    public synchronized int size() {
        return count;
    }

    public synchronized boolean isEmpty() {
        return head == null;
    }

    public synchronized boolean contains(Object o) {
        if (o == null) return false;
        for (Node p = head; p != null; p = p.next) {
            if (o.equals(p.item)) {
                return true;
            }
        }
        return false;
    }

    private void unlink(Node p) {
        Node prev = p.prev;
        Node next = p.next;
        if (prev == null) {
            head = next;
        } else {
            prev.next = next;
        }
        if (next == null) {
            tail = prev;
        } else {
            next.prev = prev;
        }
        p.prev = null;
        p.next = null;
        p.item = null;
        count--;
    }

    public synchronized boolean removeFirstOccurrence(Object o) {
        if (o == null) return false;
        for (Node p = head; p != null; p = p.next) {
            if (o.equals(p.item)) {
                unlink(p);
                return true;
            }
        }
        return false;
    }

    public synchronized boolean removeLastOccurrence(Object o) {
        if (o == null) return false;
        for (Node p = tail; p != null; p = p.prev) {
            if (o.equals(p.item)) {
                unlink(p);
                return true;
            }
        }
        return false;
    }

    public synchronized boolean remove(Object o) {
        return removeFirstOccurrence(o);
    }

    public synchronized void clear() {
        Node p = head;
        while (p != null) {
            Node next = p.next;
            p.item = null;
            p.prev = null;
            p.next = null;
            p = next;
        }
        head = null;
        tail = null;
        count = 0;
    }

    public synchronized Iterator iterator() {
        ArrayList snapshot = new ArrayList();
        for (Node p = head; p != null; p = p.next) {
            snapshot.add(p.item);
        }
        return new Itr(snapshot);
    }

    public synchronized Iterator descendingIterator() {
        ArrayList snapshot = new ArrayList();
        for (Node p = tail; p != null; p = p.prev) {
            snapshot.add(p.item);
        }
        return new Itr(snapshot);
    }

    private final class Itr implements Iterator {
        private final ArrayList snapshot;
        private int cursor;
        private int lastRet;

        Itr(ArrayList snapshot) {
            this.snapshot = snapshot;
            this.cursor = 0;
            this.lastRet = -1;
        }

        public boolean hasNext() {
            return cursor < snapshot.size();
        }

        public Object next() {
            if (cursor >= snapshot.size()) {
                throw new NoSuchElementException();
            }
            lastRet = cursor;
            Object o = snapshot.get(cursor);
            cursor++;
            return o;
        }

        public void remove() {
            if (lastRet < 0) {
                throw new IllegalStateException();
            }
            Object o = snapshot.get(lastRet);
            ConcurrentLinkedDeque.this.remove(o);
            lastRet = -1;
        }
    }

    public synchronized String toString() {
        if (head == null) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        boolean first = true;
        for (Node p = head; p != null; p = p.next) {
            if (!first) {
                sb.append(',').append(' ');
            }
            Object item = p.item;
            sb.append(item == this ? "(this Collection)" : String.valueOf(item));
            first = false;
        }
        sb.append(']');
        return sb.toString();
    }
}
