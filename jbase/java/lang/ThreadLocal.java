package java.lang;

import java.util.function.Supplier;

/**
 * Clean-room java.lang.ThreadLocal for Jebena, written from the Java SE spec.
 *
 * <p>A ThreadLocal provides thread-confined variables: in real Java each thread
 * that touches the variable keeps its own independently initialized copy, keyed
 * off the running Thread. Jebena currently executes single-threaded, so a single
 * backing store is behaviourally indistinguishable from a per-thread map for all
 * single-threaded programs. We therefore hold the value directly in two fields:
 * {@code value} plus a {@code hasValue} flag that distinguishes "no value yet"
 * (get() must lazily initialize) from "a value is stored" (including a stored
 * {@code null}). This mirrors OpenJDK's ThreadLocalMap.Entry semantics for the
 * one thread that exists.
 *
 * @param <T> the type of the thread-local value
 */
public class ThreadLocal<T> {

    // The currently stored value. Meaningful only when hasValue is true.
    private T value;

    // False means no value has been established since construction or the last
    // remove(); the next get() must run initialValue(). True means a value is
    // stored (possibly null via set(null)) and initialValue() must NOT run.
    private boolean hasValue;

    /**
     * Creates a thread-local variable.
     */
    public ThreadLocal() {
    }

    /**
     * Returns the current thread's initial value for this variable. Invoked at
     * most once per initialization cycle: the first time get() is called before
     * any set(), unless the value was already established by set(). If get() is
     * never called before set(), this method is never invoked. After remove(),
     * the next get() invokes it again.
     *
     * <p>The default implementation returns {@code null}; subclasses (and the
     * ThreadLocal returned by {@link #withInitial}) override it.
     *
     * @return the initial value for this thread-local
     */
    protected T initialValue() {
        return null;
    }

    /**
     * Establishes the initial value via {@link #initialValue()}, stores it so
     * subsequent get() calls return the same object, and returns it.
     */
    private T setInitialValue() {
        T v = initialValue();
        this.value = v;
        this.hasValue = true;
        return v;
    }

    /**
     * Returns the value in the current thread's copy of this variable. If no
     * value is currently stored, it is first initialized via
     * {@link #initialValue()} and that result is stored.
     *
     * @return the current thread's value of this thread-local
     */
    public T get() {
        if (hasValue) {
            return value;
        }
        return setInitialValue();
    }

    /**
     * Sets the current thread's copy of this variable to the specified value.
     * A stored value (including {@code null}) suppresses future
     * {@link #initialValue()} calls until {@link #remove()} is invoked.
     *
     * @param value the value to store
     */
    public void set(T value) {
        this.value = value;
        this.hasValue = true;
    }

    /**
     * Removes the current thread's value for this variable. After this call, the
     * next {@link #get()} re-invokes {@link #initialValue()}.
     */
    public void remove() {
        this.value = null;
        this.hasValue = false;
    }

    /**
     * Creates a thread-local variable whose initial value is produced by the
     * given supplier.
     *
     * @param <S>      the type of the thread-local value
     * @param supplier the supplier used to determine the initial value
     * @return a new thread-local variable
     * @throws NullPointerException if the specified supplier is null
     */
    public static <S> ThreadLocal<S> withInitial(Supplier<? extends S> supplier) {
        return new SuppliedThreadLocal<>(supplier);
    }

    /**
     * A ThreadLocal whose {@link #initialValue()} delegates to a Supplier, as
     * created by {@link #withInitial}.
     */
    static final class SuppliedThreadLocal<T> extends ThreadLocal<T> {

        private final Supplier<? extends T> supplier;

        SuppliedThreadLocal(Supplier<? extends T> supplier) {
            if (supplier == null) {
                throw new NullPointerException();
            }
            this.supplier = supplier;
        }

        @Override
        protected T initialValue() {
            return supplier.get();
        }
    }
}
