package java.util.concurrent;

// Clean-room counting Semaphore built on intrinsic monitors + wait/notify.
// acquire() blocks while no permits are available; release() returns a permit and
// wakes a waiter. Non-fair (permits are not handed out in FIFO order).
public class Semaphore {
    private int permits;
    private final Object lock = new Object();

    public Semaphore(int permits) {
        this.permits = permits;
    }

    public Semaphore(int permits, boolean fair) {
        this.permits = permits; // fairness not modeled; semantics otherwise correct
    }

    public void acquire() throws InterruptedException {
        synchronized (lock) {
            while (permits <= 0) {
                lock.wait();
            }
            permits--;
        }
    }

    public void acquire(int n) throws InterruptedException {
        if (n < 0) throw new IllegalArgumentException();
        synchronized (lock) {
            while (permits < n) {
                lock.wait();
            }
            permits -= n;
        }
    }

    public boolean tryAcquire() {
        synchronized (lock) {
            if (permits > 0) {
                permits--;
                return true;
            }
            return false;
        }
    }

    public void release() {
        synchronized (lock) {
            permits++;
            lock.notifyAll();
        }
    }

    public void release(int n) {
        if (n < 0) throw new IllegalArgumentException();
        synchronized (lock) {
            permits += n;
            lock.notifyAll();
        }
    }

    public int availablePermits() {
        synchronized (lock) {
            return permits;
        }
    }
}
