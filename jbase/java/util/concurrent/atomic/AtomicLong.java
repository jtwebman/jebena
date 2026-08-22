package java.util.concurrent.atomic;

/**
 * Clean-room java.util.concurrent.atomic.AtomicLong for Jebena. Single-threaded
 * runtime, so the value is a plain long field updated in place; the operations
 * match the observable behavior of the real atomic long.
 */
public class AtomicLong extends Number {
    private long value;

    public AtomicLong(long initialValue) {
        this.value = initialValue;
    }

    public AtomicLong() {
        this.value = 0L;
    }

    public final long get() {
        return value;
    }

    public final void set(long newValue) {
        this.value = newValue;
    }

    public final long getAndSet(long newValue) {
        long old = value;
        value = newValue;
        return old;
    }

    public final long getAndIncrement() {
        long old = value;
        value = old + 1L;
        return old;
    }

    public final long getAndDecrement() {
        long old = value;
        value = old - 1L;
        return old;
    }

    public final long getAndAdd(long delta) {
        long old = value;
        value = old + delta;
        return old;
    }

    public final long incrementAndGet() {
        value = value + 1L;
        return value;
    }

    public final long decrementAndGet() {
        value = value - 1L;
        return value;
    }

    public final long addAndGet(long delta) {
        value = value + delta;
        return value;
    }

    public final boolean compareAndSet(long expect, long update) {
        if (value == expect) {
            value = update;
            return true;
        }
        return false;
    }

    public int intValue() {
        return (int) value;
    }

    public long longValue() {
        return value;
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
