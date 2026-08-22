package st;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * java.util.concurrent thread-pool stress: a fixed pool of 4 worker fibers runs
 * 100 Callables (each returns its index); the main fiber collects all 100 Futures
 * and sums their get() -> 0..99 = 4950. Exercises the pool end to end -- workers
 * park on the queue's take(), Future.get() parks until each task completes -- so
 * it runs at carriers=1 as well as 4. Deterministic; matches real java.
 */
public class ExecutorStress {
    static final class Task implements Callable {
        private final int k;

        Task(int k) {
            this.k = k;
        }

        public Object call() {
            return Integer.valueOf(k);
        }
    }

    public static int demo() {
        try {
            ExecutorService pool = Executors.newFixedThreadPool(4);
            ArrayList futures = new ArrayList();
            for (int i = 0; i < 100; i++) {
                futures.add(pool.submit(new Task(i)));
            }
            int sum = 0;
            for (int i = 0; i < futures.size(); i++) {
                Future f = (Future) futures.get(i);
                sum += ((Integer) f.get()).intValue();
            }
            pool.shutdown();
            return sum; // 0..99 = 4950
        } catch (Exception e) {
            return -1;
        }
    }
}
