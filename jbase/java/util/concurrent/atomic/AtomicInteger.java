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

    public final native int getAndSet(int newValue);

    public final native int getAndIncrement();

    public final native int getAndDecrement();

    public final native int getAndAdd(int delta);

    public final native int incrementAndGet();

    public final native int decrementAndGet();

    public final native int addAndGet(int delta);

    public final native boolean compareAndSet(int expect, int update);

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
