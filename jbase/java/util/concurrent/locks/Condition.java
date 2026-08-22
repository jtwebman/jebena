package java.util.concurrent.locks;

// Clean-room Condition (core subset). await() must be called holding the lock;
// it atomically releases the lock and blocks until signalled, then reacquires.
public interface Condition {
    void await() throws InterruptedException;
    void signal();
    void signalAll();
}
