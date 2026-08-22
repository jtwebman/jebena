package st;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

// LinkedBlockingQueue producer/consumer with a DISTINCT-value checksum: 6 producers
// each put 200 distinct values (p*10000 + j, j=1..200); 3 consumers take() and
// accumulate into an AtomicLong until a poison pill (-1). Because every value is
// unique, a lost/duplicated/reordered item changes the sum. Expected total =
// sum_p(2,000,000*p + 20100) for p=0..5 = 30,000,000 + 120,600 = 30,120,600.
public class LbqStress {
    static final LinkedBlockingQueue<Integer> q = new LinkedBlockingQueue<>();
    static final AtomicLong sum = new AtomicLong(0);

    public static int demo() throws Exception {
        sum.set(0);
        Thread[] cons = new Thread[3];
        for (int i = 0; i < 3; i++) {
            cons[i] = new Thread(() -> {
                while (true) {
                    try {
                        int v = q.take();
                        if (v < 0) {
                            break;
                        }
                        sum.addAndGet(v);
                    } catch (InterruptedException e) {
                    }
                }
            });
        }
        Thread[] prod = new Thread[6];
        for (int p = 0; p < 6; p++) {
            final int pid = p;
            prod[p] = new Thread(() -> {
                for (int j = 1; j <= 200; j++) {
                    try {
                        q.put(Integer.valueOf(pid * 10000 + j));
                    } catch (InterruptedException e) {
                    }
                }
            });
        }
        for (int i = 0; i < 3; i++) cons[i].start();
        for (int i = 0; i < 6; i++) prod[i].start();
        for (int i = 0; i < 6; i++) prod[i].join();
        for (int i = 0; i < 3; i++) q.put(Integer.valueOf(-1));
        for (int i = 0; i < 3; i++) cons[i].join();
        return (int) sum.get();
    }
}
