package st;

import java.util.concurrent.locks.ReentrantLock;

// ReentrantLock mutual exclusion + reentrancy: 8 fibers x1000 do a NESTED
// lock/lock/count++/unlock/unlock on a shared non-atomic counter. Correct lock =>
// 8000; reentrancy must let the same thread re-acquire.
public class RLockStress {
    static final ReentrantLock lock = new ReentrantLock();
    static int count = 0;

    public static int demo() throws Exception {
        count = 0;
        Thread[] ts = new Thread[8];
        for (int i = 0; i < 8; i++) {
            ts[i] = new Thread(() -> {
                for (int j = 0; j < 1000; j++) {
                    lock.lock();
                    lock.lock(); // reentrant
                    try {
                        count++;
                    } finally {
                        lock.unlock();
                        lock.unlock();
                    }
                }
            });
        }
        for (int i = 0; i < 8; i++) ts[i].start();
        for (int i = 0; i < 8; i++) ts[i].join();
        return count;
    }
}
