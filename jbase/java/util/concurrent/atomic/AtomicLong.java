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

    public final native long getAndSet(long newValue);

    public final native long getAndIncrement();

    public final native long getAndDecrement();

    public final native long getAndAdd(long delta);

    public final native long incrementAndGet();

    public final native long decrementAndGet();

    public final native long addAndGet(long delta);

    public final native boolean compareAndSet(long expect, long update);

    public final synchronized long getAndUpdate(java.util.function.LongUnaryOperator updateFunction) {
        long old = value;
        value = updateFunction.applyAsLong(old);
        return old;
    }

    public final synchronized long updateAndGet(java.util.function.LongUnaryOperator updateFunction) {
        value = updateFunction.applyAsLong(value);
        return value;
    }

    public final synchronized long getAndAccumulate(long x, java.util.function.LongBinaryOperator accumulatorFunction) {
        long old = value;
        value = accumulatorFunction.applyAsLong(old, x);
        return old;
    }

    public final synchronized long accumulateAndGet(long x, java.util.function.LongBinaryOperator accumulatorFunction) {
        value = accumulatorFunction.applyAsLong(value, x);
        return value;
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
