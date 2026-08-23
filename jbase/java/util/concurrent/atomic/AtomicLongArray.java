package java.util.concurrent.atomic;

/**
 * Clean-room java.util.concurrent.atomic.AtomicLongArray for Jebena. Backed by a
 * plain long[]; every accessor is synchronized so updates are atomic and
 * observable across threads. Behavior matches the documented public contract.
 */
public class AtomicLongArray {
    private final long[] array;

    public AtomicLongArray(int length) {
        this.array = new long[length];
    }

    public AtomicLongArray(long[] array) {
        this.array = new long[array.length];
        for (int i = 0; i < array.length; i++) {
            this.array[i] = array[i];
        }
    }

    public final int length() {
        return array.length;
    }

    public final synchronized long get(int i) {
        return array[i];
    }

    public final synchronized void set(int i, long newValue) {
        array[i] = newValue;
    }

    public final synchronized long getAndSet(int i, long newValue) {
        long old = array[i];
        array[i] = newValue;
        return old;
    }

    public final synchronized long getAndAdd(int i, long delta) {
        long old = array[i];
        array[i] = old + delta;
        return old;
    }

    public final synchronized long addAndGet(int i, long delta) {
        array[i] = array[i] + delta;
        return array[i];
    }

    public final synchronized long getAndIncrement(int i) {
        long old = array[i];
        array[i] = old + 1L;
        return old;
    }

    public final synchronized long getAndDecrement(int i) {
        long old = array[i];
        array[i] = old - 1L;
        return old;
    }

    public final synchronized long incrementAndGet(int i) {
        array[i] = array[i] + 1L;
        return array[i];
    }

    public final synchronized long decrementAndGet(int i) {
        array[i] = array[i] - 1L;
        return array[i];
    }

    public final synchronized boolean compareAndSet(int i, long expect, long update) {
        if (array[i] == expect) {
            array[i] = update;
            return true;
        }
        return false;
    }

    public synchronized String toString() {
        int last = array.length - 1;
        if (last == -1) {
            return "[]";
        }
        StringBuilder b = new StringBuilder();
        b.append('[');
        for (int i = 0; ; i++) {
            b.append(array[i]);
            if (i == last) {
                return b.append(']').toString();
            }
            b.append(',').append(' ');
        }
    }
}
