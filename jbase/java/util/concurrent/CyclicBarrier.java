package java.util.concurrent;

import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;

// Clean-room CyclicBarrier on ReentrantLock + Condition. Each generation, the last
// of `parties` to await() runs the optional barrier action, resets the count, and
// trips the barrier (waking the rest); the barrier is reusable across generations.
// This is the feature that first exposed the need for real fiber parking (all
// parties block simultaneously) -- now that blocking parks, it works at N>1.
public class CyclicBarrier {
    private final int parties;
    private final Runnable barrierAction;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition trip = lock.newCondition();
    private int count;
    private int generation;

    public CyclicBarrier(int parties) {
        this(parties, null);
    }

    public CyclicBarrier(int parties, Runnable barrierAction) {
        if (parties <= 0) throw new IllegalArgumentException();
        this.parties = parties;
        this.count = parties;
        this.barrierAction = barrierAction;
    }

    // Returns the arrival index: parties-1 for the first to arrive, 0 for the last.
    public int await() throws InterruptedException, BrokenBarrierException {
        lock.lock();
        try {
            int g = generation;
            int index = --count;
            if (index == 0) {
                if (barrierAction != null) barrierAction.run();
                count = parties;
                generation++;
                trip.signalAll();
                return 0;
            }
            while (g == generation) {
                trip.await();
            }
            return index;
        } finally {
            lock.unlock();
        }
    }

    public int getParties() {
        return parties;
    }

    public int getNumberWaiting() {
        lock.lock();
        try {
            return parties - count;
        } finally {
            lock.unlock();
        }
    }
}
