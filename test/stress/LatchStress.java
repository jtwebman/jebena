package st;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

// CountDownLatch: 8 workers each do work then countDown(); main await()s the latch,
// which must guarantee all work completed before it returns. Deterministic: 8000.
public class LatchStress {
    static final AtomicInteger sum = new AtomicInteger(0);
    static CountDownLatch latch;

    public static int demo() throws Exception {
        sum.set(0);
        latch = new CountDownLatch(8);
        Thread[] ts = new Thread[8];
        for (int i = 0; i < 8; i++) {
            ts[i] = new Thread(() -> {
                for (int j = 0; j < 1000; j++) sum.incrementAndGet();
                latch.countDown();
            });
        }
        for (int i = 0; i < 8; i++) ts[i].start();
        latch.await();
        return sum.get();
    }
}
