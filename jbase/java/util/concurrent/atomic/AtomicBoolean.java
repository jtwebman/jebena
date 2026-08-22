package java.util.concurrent.atomic;

/**
 * Clean-room java.util.concurrent.atomic.AtomicBoolean for Jebena. The flag is
 * held in a plain boolean field; on a single-threaded runtime the plain
 * read-modify-write operations reproduce the atomic behavior exactly.
 */
public class AtomicBoolean {
    private boolean value;

    public AtomicBoolean(boolean initialValue) {
        this.value = initialValue;
    }

    public AtomicBoolean() {
        this.value = false;
    }

    public final boolean get() {
        return value;
    }

    public final void set(boolean newValue) {
        this.value = newValue;
    }

    public final boolean getAndSet(boolean newValue) {
        boolean old = value;
        value = newValue;
        return old;
    }

    public final boolean compareAndSet(boolean expect, boolean update) {
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
