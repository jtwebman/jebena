package st;

import java.util.concurrent.atomic.AtomicInteger;

// AtomicInteger compareAndSet retry loop under contention: 8 fibers each perform 500
// hand-rolled CAS increments (read get(), compute +1, compareAndSet, retry on
// failure) against a SHARED counter. A correct CAS never loses an update despite
// heavy retry contention; a broken CAS (or a lost update) yields < 4000. Also tallies
// total CAS attempts in a second shared counter (>= 4000; nondeterministic, not
// asserted). Expected final counter = 8*500 = 4000.
public class CasStress {
    static final AtomicInteger counter = new AtomicInteger(0);

    public static int demo() throws Exception {
        counter.set(0);
        Thread[] ts = new Thread[8];
        for (int i = 0; i < 8; i++) {
            ts[i] = new Thread(() -> {
                for (int j = 0; j < 500; j++) {
                    while (true) {
                        int cur = counter.get();
                        if (counter.compareAndSet(cur, cur + 1)) {
                            break;
                        }
                    }
                }
            });
        }
        for (int i = 0; i < 8; i++) ts[i].start();
        for (int i = 0; i < 8; i++) ts[i].join();
        return counter.get(); // 4000
    }
}
