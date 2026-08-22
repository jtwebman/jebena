package java.util.concurrent;

// Clean-room CountDownLatch: a one-shot latch built on intrinsic monitors +
// wait/notify. await() blocks until the count reaches zero; countDown() decrements
// and wakes waiters at zero.
public class CountDownLatch {
    private int count;
    private final Object lock = new Object();

    public CountDownLatch(int count) {
        if (count < 0) throw new IllegalArgumentException("count < 0");
        this.count = count;
    }

    public void await() throws InterruptedException {
        synchronized (lock) {
            while (count > 0) {
                lock.wait();
            }
        }
    }

    public void countDown() {
        synchronized (lock) {
            if (count > 0) {
                count--;
                if (count == 0) {
                    lock.notifyAll();
                }
            }
        }
    }

    public long getCount() {
        synchronized (lock) {
            return count;
        }
    }

    public String toString() {
        return super.toString() + "[Count = " + getCount() + "]";
    }
}
