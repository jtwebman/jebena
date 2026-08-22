package java.util.concurrent.locks;

// Condition backed by a private wait object. await() holds the wait object's
// monitor while releasing the lock, so a concurrent signal() (which needs that
// same monitor) cannot slip in before the wait begins -- no lost wakeup.
class CondObject implements Condition {
    private final ReentrantLock lock;
    private final Object cvar = new Object();

    CondObject(ReentrantLock lock) {
        this.lock = lock;
    }

    public void await() throws InterruptedException {
        int saved;
        synchronized (cvar) {
            saved = lock.fullyRelease();
            cvar.wait();
        }
        lock.reacquire(saved);
    }

    public void signal() {
        synchronized (cvar) {
            cvar.notify();
        }
    }

    public void signalAll() {
        synchronized (cvar) {
            cvar.notifyAll();
        }
    }
}
