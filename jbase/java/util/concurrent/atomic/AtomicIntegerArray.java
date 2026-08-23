package java.util.concurrent.atomic;

/**
 * Clean-room java.util.concurrent.atomic.AtomicIntegerArray for Jebena. The
 * element storage is an ordinary int[]; every accessor is synchronized so the
 * whole read-modify-write sequence is atomic with respect to other callers,
 * reproducing the observable behavior of the JDK's per-element atomics.
 */
public class AtomicIntegerArray {
    private final int[] array;

    public AtomicIntegerArray(int length) {
        this.array = new int[length];
    }

    public AtomicIntegerArray(int[] array) {
        this.array = new int[array.length];
        for (int i = 0; i < array.length; i++) {
            this.array[i] = array[i];
        }
    }

    public final int length() {
        return array.length;
    }

    public final synchronized int get(int i) {
        return array[i];
    }

    public final synchronized void set(int i, int newValue) {
        array[i] = newValue;
    }

    public final synchronized int getAndSet(int i, int newValue) {
        int old = array[i];
        array[i] = newValue;
        return old;
    }

    public final synchronized int getAndAdd(int i, int delta) {
        int old = array[i];
        array[i] = old + delta;
        return old;
    }

    public final synchronized int addAndGet(int i, int delta) {
        int result = array[i] + delta;
        array[i] = result;
        return result;
    }

    public final synchronized int getAndIncrement(int i) {
        int old = array[i];
        array[i] = old + 1;
        return old;
    }

    public final synchronized int getAndDecrement(int i) {
        int old = array[i];
        array[i] = old - 1;
        return old;
    }

    public final synchronized int incrementAndGet(int i) {
        int result = array[i] + 1;
        array[i] = result;
        return result;
    }

    public final synchronized int decrementAndGet(int i) {
        int result = array[i] - 1;
        array[i] = result;
        return result;
    }

    public final synchronized boolean compareAndSet(int i, int expect, int update) {
        if (array[i] == expect) {
            array[i] = update;
            return true;
        }
        return false;
    }

    public String toString() {
        int iMax = array.length - 1;
        if (iMax == -1) {
            return "[]";
        }
        StringBuilder b = new StringBuilder();
        b.append('[');
        for (int i = 0; ; i++) {
            b.append(get(i));
            if (i == iMax) {
                return b.append(']').toString();
            }
            b.append(',').append(' ');
        }
    }
}
