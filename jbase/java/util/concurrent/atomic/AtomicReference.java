package java.util.concurrent.atomic;

/**
 * Clean-room java.util.concurrent.atomic.AtomicReference for Jebena. The
 * referent lives in a plain Object field; compareAndSet uses reference identity
 * (==), matching the spec. Mutators/accessors are synchronized on the instance
 * so compareAndSet/getAndSet are atomic across carriers (reentrant monitor).
 */
public class AtomicReference {
    private Object value;

    public AtomicReference(Object initialValue) {
        this.value = initialValue;
    }

    public AtomicReference() {
        this.value = null;
    }

    public final synchronized Object get() {
        return value;
    }

    public final synchronized void set(Object newValue) {
        this.value = newValue;
    }

    public final synchronized Object getAndSet(Object newValue) {
        Object old = value;
        value = newValue;
        return old;
    }

    public final synchronized boolean compareAndSet(Object expect, Object update) {
        if (value == expect) {
            value = update;
            return true;
        }
        return false;
    }

    public final synchronized Object getAndUpdate(java.util.function.UnaryOperator updateFunction) {
        Object old = value;
        value = updateFunction.apply(old);
        return old;
    }

    public final synchronized Object updateAndGet(java.util.function.UnaryOperator updateFunction) {
        value = updateFunction.apply(value);
        return value;
    }

    public final synchronized Object getAndAccumulate(Object x, java.util.function.BinaryOperator accumulatorFunction) {
        Object old = value;
        value = accumulatorFunction.apply(old, x);
        return old;
    }

    public final synchronized Object accumulateAndGet(Object x, java.util.function.BinaryOperator accumulatorFunction) {
        value = accumulatorFunction.apply(value, x);
        return value;
    }

    public String toString() {
        return String.valueOf(get());
    }
}
