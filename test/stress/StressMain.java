package st;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

// Stress: many green-thread fibers hammering a shared AtomicInteger, joined by
// main. At one carrier this validates the scheduler + join + atomics under load;
// once real carriers (JEBENA_CARRIERS>1) spawn, the same test exercises true
// parallel updates (must still total exactly). Lambda captures nothing (uses the
// static counter + literal bound) to stay within tested lambda support.
public class StressMain {
    static final AtomicInteger counter = new AtomicInteger(0);
    static final AtomicLong lcounter = new AtomicLong(0L);

    public static int demo() throws Exception {
        Thread[] ts = new Thread[8];
        for (int i = 0; i < 8; i++) {
            ts[i] = new Thread(() -> {
                for (int j = 0; j < 1000; j++) {
                    counter.incrementAndGet();
                    lcounter.addAndGet(2L);
                }
            });
        }
        for (int i = 0; i < 8; i++) {
            ts[i].start();
        }
        for (int i = 0; i < 8; i++) {
            ts[i].join();
        }
        return counter.get() + (int) lcounter.get(); // 8000 + 16000 = 24000
    }
}
