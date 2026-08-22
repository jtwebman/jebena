package st;

import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;

// ReentrantLock + Condition handoff: 8 workers each, under the lock, decrement
// `remaining` and signal(); main await()s (releasing+reacquiring the lock) until
// remaining==0. await must release the lock so workers can enter. Result: 8.
public class CondStress {
    static final ReentrantLock lock = new ReentrantLock();
    static final Condition cond = lock.newCondition();
    static int remaining;
    static int sum;

    public static int demo() throws Exception {
        remaining = 8;
        sum = 0;
        Thread[] ts = new Thread[8];
        for (int i = 0; i < 8; i++) {
            ts[i] = new Thread(() -> {
                lock.lock();
                try {
                    sum += 1;
                    remaining -= 1;
                    cond.signal();
                } finally {
                    lock.unlock();
                }
            });
        }
        for (int i = 0; i < 8; i++) ts[i].start();
        lock.lock();
        try {
            while (remaining > 0) cond.await();
        } finally {
            lock.unlock();
        }
        for (int i = 0; i < 8; i++) ts[i].join();
        return sum;
    }
}
