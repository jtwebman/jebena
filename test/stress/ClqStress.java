package st;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

// ConcurrentLinkedQueue thread-safety with a DISTINCT-value checksum, structured to fit the
// cooperative M:N scheduler (ConcurrentLinkedQueue is NON-blocking, so simultaneous
// produce+consume would busy-wait — and jbase Thread.yield() is a no-op — so we use two
// concurrent phases instead of a spin loop):
//   phase 1: 6 producers concurrently offer 200 distinct values each (p*10000+j) -> join;
//   phase 2: 3 consumers concurrently drain via poll() until it returns null (empty) -> join.
// Concurrent offers must not lose/dup items and concurrent polls must not dup/skip, so
// sum=30,120,600 and consumed=1200 -> demo()=30,121,800 deterministically. Must match real
// java at carriers 1 & 2 and with GC forced.
public class ClqStress {
    static final ConcurrentLinkedQueue q = new ConcurrentLinkedQueue();
    static final AtomicLong sum = new AtomicLong(0);
    static final AtomicInteger consumed = new AtomicInteger(0);

    public static int demo() throws Exception {
        q.clear();
        sum.set(0);
        consumed.set(0);

        Thread[] prod = new Thread[6];
        for (int i = 0; i < 6; i++) {
            final int pid = i;
            prod[i] = new Thread(() -> {
                for (int j = 1; j <= 200; j++) {
                    q.offer(Integer.valueOf(pid * 10000 + j));
                }
            });
        }
        for (int i = 0; i < 6; i++) {
            prod[i].start();
        }
        for (int i = 0; i < 6; i++) {
            prod[i].join();
        }

        Thread[] cons = new Thread[3];
        for (int i = 0; i < 3; i++) {
            cons[i] = new Thread(() -> {
                while (true) {
                    Object v = q.poll();
                    if (v == null) {
                        break; // queue drained (all producers already finished)
                    }
                    sum.addAndGet(((Integer) v).intValue());
                    consumed.incrementAndGet();
                }
            });
        }
        for (int i = 0; i < 3; i++) {
            cons[i].start();
        }
        for (int i = 0; i < 3; i++) {
            cons[i].join();
        }
        return (int) sum.get() + consumed.get();
    }
}
