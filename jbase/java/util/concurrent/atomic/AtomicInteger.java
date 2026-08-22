package java.util.concurrent.atomic;

/**
 * Clean-room java.util.concurrent.atomic.AtomicInteger for Jebena. Jebena is
 * single-threaded, so the value is held in an ordinary int field and every
 * operation is a plain read-modify-write; this reproduces the observable
 * behavior of the atomic operations without any memory-ordering machinery.
 */
public class AtomicInteger extends Number {
    private int value;

    public AtomicInteger(int initialValue) {
        this.value = initialValue;
    }

    public AtomicInteger() {
        this.value = 0;
    }

    public final int get() {
        return value;
    }

    public final void set(int newValue) {
        this.value = newValue;
    }

    public final int getAndSet(int newValue) {
        int old = value;
        value = newValue;
        return old;
    }

    public final int getAndIncrement() {
        int old = value;
        value = old + 1;
        return old;
    }

    public final int getAndDecrement() {
        int old = value;
        value = old - 1;
        return old;
    }

    public final int getAndAdd(int delta) {
        int old = value;
        value = old + delta;
        return old;
    }

    public final int incrementAndGet() {
        value = value + 1;
        return value;
    }

    public final int decrementAndGet() {
        value = value - 1;
        return value;
    }

    public final int addAndGet(int delta) {
        value = value + delta;
        return value;
    }

    public final boolean compareAndSet(int expect, int update) {
        if (value == expect) {
            value = update;
            return true;
        }
        return false;
    }

    public int intValue() {
        return value;
    }

    public long longValue() {
        return (long) value;
    }

    public float floatValue() {
        return (float) value;
    }

    public double doubleValue() {
        return (double) value;
    }

    public String toString() {
        return String.valueOf(get());
    }
}
