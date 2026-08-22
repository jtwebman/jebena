package java.util.concurrent;

/** Clean-room java.util.concurrent.Future (raw: get() returns Object). */
public interface Future {
    Object get() throws Exception;

    boolean isDone();
}
