package java.util.concurrent;

// Clean-room BlockingQueue (core subset). put/take block via the implementation's
// lock+condition (parking); offer/poll are non-blocking.
public interface BlockingQueue<E> {
    void put(E e) throws InterruptedException;

    E take() throws InterruptedException;

    boolean offer(E e);

    E poll();

    int size();

    boolean isEmpty();
}
