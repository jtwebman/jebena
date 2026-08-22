package java.util.concurrent;

/** Clean-room java.util.concurrent.Callable (raw/non-generic: returns Object). */
public interface Callable {
    Object call() throws Exception;
}
