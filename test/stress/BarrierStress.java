package st;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

// CyclicBarrier reuse: 4 workers x 100 rounds each await() and add the returned
// arrival index to a shared sum. Per round the four indices are exactly {0,1,2,3}
// (sum 6) regardless of order, so total = 100*6 = 600. Deadlocked under the old
// spin model (all parties block at once); works now that blocking parks.
public class BarrierStress {
    static final AtomicInteger sum = new AtomicInteger(0);
    static CyclicBarrier barrier;

    public static int demo() throws Exception {
        sum.set(0);
        barrier = new CyclicBarrier(4);
        Thread[] ts = new Thread[4];
        for (int i = 0; i < 4; i++) {
            ts[i] = new Thread(() -> {
                for (int r = 0; r < 100; r++) {
                    try {
                        sum.addAndGet(barrier.await());
                    } catch (Exception e) {
                    }
                }
            });
        }
        for (int i = 0; i < 4; i++) ts[i].start();
        for (int i = 0; i < 4; i++) ts[i].join();
        return sum.get();
    }
}
