package java.util.concurrent.locks;

// Clean-room reentrant mutual-exclusion lock, built on intrinsic monitors +
// wait/notify + per-thread Thread.currentThread() identity. Non-fair.
public class ReentrantLock implements Lock {
    private final Object mutex = new Object();
    private Thread owner;
    private int holds;

    public ReentrantLock() {
    }

    public ReentrantLock(boolean fair) {
        // fairness not modeled; mutual exclusion + reentrancy are correct
    }

    public void lock() {
        Thread me = Thread.currentThread();
        synchronized (mutex) {
            if (owner == me) {
                holds++;
                return;
            }
            while (owner != null) {
                mutex.wait(); // uninterruptible; jbase wait() does not throw
            }
            owner = me;
            holds = 1;
        }
    }

    public boolean tryLock() {
        Thread me = Thread.currentThread();
        synchronized (mutex) {
            if (owner == null) {
                owner = me;
                holds = 1;
                return true;
            }
            if (owner == me) {
                holds++;
                return true;
            }
            return false;
        }
    }

    public void unlock() {
        Thread me = Thread.currentThread();
        synchronized (mutex) {
            if (owner != me) {
                throw new IllegalMonitorStateException();
            }
            holds--;
            if (holds == 0) {
                owner = null;
                mutex.notify();
            }
        }
    }

    public boolean isLocked() {
        synchronized (mutex) {
            return owner != null;
        }
    }

    public boolean isHeldByCurrentThread() {
        synchronized (mutex) {
            return owner == Thread.currentThread();
        }
    }

    public int getHoldCount() {
        synchronized (mutex) {
            return owner == Thread.currentThread() ? holds : 0;
        }
    }

    public Condition newCondition() {
        return new CondObject(this);
    }

    // --- support for Condition.await(): fully release / reacquire the lock ---

    int fullyRelease() {
        Thread me = Thread.currentThread();
        synchronized (mutex) {
            if (owner != me) {
                throw new IllegalMonitorStateException();
            }
            int h = holds;
            owner = null;
            holds = 0;
            mutex.notifyAll();
            return h;
        }
    }

    void reacquire(int h) {
        Thread me = Thread.currentThread();
        synchronized (mutex) {
            while (owner != null) {
                mutex.wait();
            }
            owner = me;
            holds = h;
        }
    }
}
