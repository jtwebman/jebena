package st;

import java.util.concurrent.atomic.AtomicInteger;

// Parking scale test: 16 joiner fibers each Thread.join() a single long worker,
// then add 1; main joins all 16. With real parking the 16 blocked joiners yield
// their carriers (so the worker + others still run) -- impossible under spin-wait
// where 16 blocked > 4 carriers would deadlock. Deterministic: worker adds 1000,
// 16 joiners add 1 each => 1016.
public class JoinStress {
    static final AtomicInteger sum = new AtomicInteger(0);
    static Thread worker;

    public static int demo() throws Exception {
        sum.set(0);
        worker = new Thread(() -> {
            long x = 0;
            for (int i = 0; i < 300000; i++) x += i;
            if (x != 0) sum.addAndGet(1000);
        });
        Thread[] joiners = new Thread[16];
        for (int i = 0; i < 16; i++) {
            joiners[i] = new Thread(() -> {
                try {
                    worker.join();
                } catch (InterruptedException e) {
                }
                sum.addAndGet(1);
            });
        }
        worker.start();
        for (int i = 0; i < 16; i++) joiners[i].start();
        for (int i = 0; i < 16; i++) joiners[i].join();
        return sum.get();
    }
}
