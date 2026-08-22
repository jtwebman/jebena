package st;

import java.util.concurrent.atomic.AtomicLong;

// Exception + allocation + concurrent-GC stress. 8 fibers each throw and catch a
// freshly-allocated RuntimeException every other iteration while allocating arrays,
// so the moving GC (with a small JEBENA_GC_INTERVAL) fires across carriers while
// exceptions are being constructed/thrown/caught. Deterministic checksum: per
// fiber, 1000 even iters (throw -> catch, +3) + 1000 odd iters (no throw, +8) =
// 11000; x8 fibers = 88000. Validates that exception handling and the moving
// collector coexist correctly under real parallelism.
public class ThrowStress {
    static final AtomicLong total = new AtomicLong(0);

    public static int demo() {
        total.set(0);
        Thread[] ts = new Thread[8];
        for (int t = 0; t < 8; t++) {
            ts[t] = new Thread(() -> {
                long local = 0;
                for (int i = 0; i < 2000; i++) {
                    try {
                        int[] a = new int[8];
                        if ((i & 1) == 0) throw new RuntimeException("boom");
                        local += a.length;
                    } catch (RuntimeException e) {
                        local += 3;
                    }
                }
                total.addAndGet(local);
            });
        }
        for (int t = 0; t < 8; t++) ts[t].start();
        for (int t = 0; t < 8; t++) {
            try {
                ts[t].join();
            } catch (InterruptedException e) {
            }
        }
        return (int) total.get();
    }
}
