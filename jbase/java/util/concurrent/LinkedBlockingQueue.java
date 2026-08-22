package java.util.concurrent;

import java.util.ArrayDeque;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;

// Clean-room LinkedBlockingQueue on ReentrantLock + two Conditions. take() parks on
// notEmpty when empty; put() parks on notFull when at capacity (unbounded by
// default). Backed by a raw LinkedList under the lock.
public class LinkedBlockingQueue<E> implements BlockingQueue<E> {
    private final ArrayDeque items = new ArrayDeque();
    private final int capacity;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final Condition notFull = lock.newCondition();

    public LinkedBlockingQueue() {
        this.capacity = 2147483647; // Integer.MAX_VALUE (not in jbase yet)
    }

    public LinkedBlockingQueue(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException();
        this.capacity = capacity;
    }

    public void put(E e) throws InterruptedException {
        lock.lock();
        try {
            while (items.size() >= capacity) notFull.await();
            items.addLast(e);
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    public E take() throws InterruptedException {
        lock.lock();
        try {
            while (items.isEmpty()) notEmpty.await();
            E e = (E) items.removeFirst();
            notFull.signal();
            return e;
        } finally {
            lock.unlock();
        }
    }

    public boolean offer(E e) {
        lock.lock();
        try {
            if (items.size() >= capacity) return false;
            items.addLast(e);
            notEmpty.signal();
            return true;
        } finally {
            lock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    public E poll() {
        lock.lock();
        try {
            if (items.isEmpty()) return null;
            E e = (E) items.removeFirst();
            notFull.signal();
            return e;
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return items.size();
        } finally {
            lock.unlock();
        }
    }

    public boolean isEmpty() {
        return size() == 0;
    }
}
