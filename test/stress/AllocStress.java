package st;

import java.util.concurrent.atomic.AtomicLong;

// Allocation stress: 8 fibers each allocate a fresh array every iteration (so the
// moving GC fires repeatedly, especially with a small JEBENA_GC_INTERVAL) while
// computing a deterministic checksum from live objects. If a collection corrupts
// or frees a still-live array, the total diverges from real java. Exercises the
// stop-the-world safepoint + moving mark-compact GC under real parallel carriers.
public class AllocStress {
    static final AtomicLong total = new AtomicLong(0);

    public static int demo() {
        total.set(0);
        Thread[] ts = new Thread[8];
        for (int t = 0; t < 8; t++) {
            ts[t] = new Thread(() -> {
                long local = 0;
                for (int i = 0; i < 2000; i++) {
                    int[] a = new int[16];
                    for (int j = 0; j < 16; j++) a[j] = i + j;
                    int s = 0;
                    for (int j = 0; j < 16; j++) s += a[j];
                    local += s;
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
