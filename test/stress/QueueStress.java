package st;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

// LinkedBlockingQueue producer/consumer: 4 producers each put 100 ones; 4 consumers
// take() and sum, blocking (parking) on the empty queue between items. After the
// producers finish, main enqueues 4 poison pills (-1) to stop the consumers.
// Deterministic total: 4*100 = 400.
public class QueueStress {
    static final LinkedBlockingQueue<Integer> q = new LinkedBlockingQueue<>();
    static final AtomicInteger sum = new AtomicInteger(0);

    public static int demo() throws Exception {
        sum.set(0);
        Thread[] cons = new Thread[4];
        for (int i = 0; i < 4; i++) {
            cons[i] = new Thread(() -> {
                while (true) {
                    try {
                        int v = q.take();
                        if (v < 0) break;
                        sum.addAndGet(v);
                    } catch (InterruptedException e) {
                    }
                }
            });
        }
        Thread[] prod = new Thread[4];
        for (int i = 0; i < 4; i++) {
            prod[i] = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    try {
                        q.put(1);
                    } catch (InterruptedException e) {
                    }
                }
            });
        }
        for (int i = 0; i < 4; i++) cons[i].start();
        for (int i = 0; i < 4; i++) prod[i].start();
        for (int i = 0; i < 4; i++) prod[i].join();
        for (int i = 0; i < 4; i++) q.put(-1);
        for (int i = 0; i < 4; i++) cons[i].join();
        return sum.get();
    }
}
