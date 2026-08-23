package java.util.concurrent;

import java.util.Queue;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.ArrayList;

// Clean-room ConcurrentLinkedQueue: an unbounded FIFO queue backed by a singly
// linked list with head/tail node references. jbase uses coarse-grained
// synchronization (every mutator/accessor is synchronized) rather than the
// lock-free CAS design of the JDK; the externally observable behavior (FIFO
// order, null-rejection, poll/peek returning null when empty) is identical.
public class ConcurrentLinkedQueue implements Queue {

    private static final class Node {
        Object item;
        Node next;

        Node(Object item) {
            this.item = item;
        }
    }

    private Node head;
    private Node tail;
    private int count;

    public ConcurrentLinkedQueue() {
        head = null;
        tail = null;
        count = 0;
    }

    public ConcurrentLinkedQueue(Collection c) {
        head = null;
        tail = null;
        count = 0;
        if (c == null) throw new NullPointerException();
        Iterator it = c.iterator();
        while (it.hasNext()) {
            offer(it.next());
        }
    }

    public synchronized boolean offer(Object e) {
        if (e == null) throw new NullPointerException();
        Node n = new Node(e);
        if (tail == null) {
            head = n;
            tail = n;
        } else {
            tail.next = n;
            tail = n;
        }
        count++;
        return true;
    }

    public synchronized boolean add(Object e) {
        return offer(e);
    }

    public synchronized Object poll() {
        if (head == null) {
            return null;
        }
        Node n = head;
        Object item = n.item;
        head = n.next;
        n.next = null;
        if (head == null) {
            tail = null;
        }
        count--;
        return item;
    }

    public synchronized Object peek() {
        return head == null ? null : head.item;
    }

    public synchronized Object remove() {
        Object x = poll();
        if (x == null) {
            throw new NoSuchElementException();
        }
        return x;
    }

    public synchronized Object element() {
        Object x = peek();
        if (x == null) {
            throw new NoSuchElementException();
        }
        return x;
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

    public synchronized boolean remove(Object o) {
        if (o == null) return false;
        Node prev = null;
        for (Node p = head; p != null; prev = p, p = p.next) {
            if (o.equals(p.item)) {
                Node next = p.next;
                if (prev == null) {
                    head = next;
                } else {
                    prev.next = next;
                }
                if (next == null) {
                    tail = prev;
                }
                p.next = null;
                count--;
                return true;
            }
        }
        return false;
    }

    public synchronized void clear() {
        Node p = head;
        while (p != null) {
            Node next = p.next;
            p.item = null;
            p.next = null;
            p = next;
        }
        head = null;
        tail = null;
        count = 0;
    }

    public synchronized Iterator iterator() {
        // Weakly-consistent snapshot iterator over the current contents.
        ArrayList snapshot = new ArrayList();
        for (Node p = head; p != null; p = p.next) {
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
            ConcurrentLinkedQueue.this.remove(o);
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
