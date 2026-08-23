package st;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicLong;

// Bounded-buffer producer/consumer over a shared ArrayDeque guarded by a user
// monitor with wait/notifyAll (capacity 8): 4 producers each put 250 DISTINCT values
// (p*1000+j), blocking when full; 4 consumers take (blocking when empty) and sum into
// an AtomicLong until a poison pill (-1). Exercises Object.wait/notifyAll on a user
// lock under backpressure both ways + moving GC. Distinct values -> any lost/dup item
// or lost wakeup breaks the sum (or hangs). Expected sum = 1,625,500.
public class BoundedBufferStress {
    static final Object lock = new Object();
    static final ArrayDeque buf = new ArrayDeque();
    static final int CAP = 8;
    static final AtomicLong consumed = new AtomicLong(0);

    public static int demo() throws Exception {
        consumed.set(0);
        final int P = 4;
        final int C = 4;
        final int perProducer = 250;

        Thread[] cons = new Thread[C];
        for (int i = 0; i < C; i++) {
            cons[i] = new Thread(() -> {
                while (true) {
                    int v;
                    synchronized (lock) {
                        while (buf.isEmpty()) {
                            try {
                                lock.wait();
                            } catch (InterruptedException e) {
                            }
                        }
                        v = ((Integer) buf.pollFirst()).intValue();
                        lock.notifyAll();
                    }
                    if (v < 0) {
                        break;
                    }
                    consumed.addAndGet(v);
                }
            });
        }
        Thread[] prod = new Thread[P];
        for (int p = 0; p < P; p++) {
            final int pid = p;
            prod[p] = new Thread(() -> {
                for (int j = 1; j <= perProducer; j++) {
                    synchronized (lock) {
                        while (buf.size() >= CAP) {
                            try {
                                lock.wait();
                            } catch (InterruptedException e) {
                            }
                        }
                        buf.addLast(Integer.valueOf(pid * 1000 + j));
                        lock.notifyAll();
                    }
                }
            });
        }
        for (int i = 0; i < C; i++) cons[i].start();
        for (int i = 0; i < P; i++) prod[i].start();
        for (int i = 0; i < P; i++) prod[i].join();
        for (int i = 0; i < C; i++) {
            synchronized (lock) {
                buf.addLast(Integer.valueOf(-1));
                lock.notifyAll();
            }
        }
        for (int i = 0; i < C; i++) cons[i].join();
        return (int) consumed.get();
    }
}
