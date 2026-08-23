package st;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

// ConcurrentLinkedDeque thread-safety across both ends, PHASED for the cooperative M:N scheduler
// (poll returns null when empty and Thread.yield() is a jbase no-op, so a drain-until-null
// consumer running concurrently with producers would busy-spin/livelock — see
// jebena-cooperative-stress-design). So: phase 1 = 6 producers concurrently push 200 distinct
// values each (even pid -> offerLast, odd pid -> offerFirst) then JOIN; phase 2 = 3 consumers
// concurrently drain (consumer 0 pollLast, others pollFirst) until null then JOIN. Order does
// not affect the checksum since every value is drained exactly once: sum = 30,120,600 over
// 1200 distinct values -> demo() = (int) sum + count = 30,121,800. Must match real java at
// carriers 1 & 2 and with GC forced.
public class CldStress {
    static final ConcurrentLinkedDeque dq = new ConcurrentLinkedDeque();
    static final AtomicLong sum = new AtomicLong(0);
    static final AtomicInteger consumed = new AtomicInteger(0);

    public static int demo() throws Exception {
        dq.clear();
        sum.set(0);
        consumed.set(0);

        Thread[] prod = new Thread[6];
        for (int i = 0; i < 6; i++) {
            final int pid = i;
            prod[i] = new Thread(() -> {
                for (int j = 1; j <= 200; j++) {
                    Integer v = Integer.valueOf(pid * 10000 + j);
                    if ((pid & 1) == 0) {
                        dq.offerLast(v);
                    } else {
                        dq.offerFirst(v);
                    }
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
            final int cid = i;
            cons[i] = new Thread(() -> {
                while (true) {
                    Object v = (cid == 0) ? dq.pollLast() : dq.pollFirst();
                    if (v == null) {
                        break; // drained (all producers already finished)
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
