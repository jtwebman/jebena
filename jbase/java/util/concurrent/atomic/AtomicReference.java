package java.util.concurrent.atomic;

/**
 * Clean-room java.util.concurrent.atomic.AtomicReference for Jebena. The
 * referent lives in a plain Object field; compareAndSet uses reference identity
 * (==), matching the spec. Single-threaded, so plain updates are correct.
 */
public class AtomicReference {
    private Object value;

    public AtomicReference(Object initialValue) {
        this.value = initialValue;
    }

    public AtomicReference() {
        this.value = null;
    }

    public final Object get() {
        return value;
    }

    public final void set(Object newValue) {
        this.value = newValue;
    }

    public final Object getAndSet(Object newValue) {
        Object old = value;
        value = newValue;
        return old;
    }

    public final boolean compareAndSet(Object expect, Object update) {
        if (value == expect) {
            value = update;
            return true;
        }
        return false;
    }

    public String toString() {
        return String.valueOf(get());
    }
}
