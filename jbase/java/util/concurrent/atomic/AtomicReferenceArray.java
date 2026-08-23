package java.util.concurrent.atomic;

/**
 * Clean-room java.util.concurrent.atomic.AtomicReferenceArray for Jebena. The
 * element storage is an ordinary Object[]; every accessor is synchronized so
 * the whole read-modify-write sequence is atomic with respect to other
 * callers, reproducing the observable behavior of the JDK's per-element
 * reference atomics. compareAndSet uses reference identity (==), matching the
 * spec.
 */
public class AtomicReferenceArray {
    private final Object[] array;

    public AtomicReferenceArray(int length) {
        this.array = new Object[length];
    }

    public AtomicReferenceArray(Object[] array) {
        this.array = new Object[array.length];
        for (int i = 0; i < array.length; i++) {
            this.array[i] = array[i];
        }
    }

    public final int length() {
        return array.length;
    }

    public final synchronized Object get(int i) {
        return array[i];
    }

    public final synchronized void set(int i, Object newValue) {
        array[i] = newValue;
    }

    public final synchronized Object getAndSet(int i, Object newValue) {
        Object old = array[i];
        array[i] = newValue;
        return old;
    }

    public final synchronized boolean compareAndSet(int i, Object expect, Object update) {
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
            b.append(String.valueOf(get(i)));
            if (i == iMax) {
                return b.append(']').toString();
            }
            b.append(',').append(' ');
        }
    }
}
