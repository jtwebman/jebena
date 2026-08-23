package st;

import java.util.concurrent.atomic.AtomicIntegerArray;

// AtomicIntegerArray contention: 8 fibers each do 500 getAndAdd across a shared 10-element
// array (index j%10, delta (j%7)+1), then 50 compareAndSet-spin increments on index 0.
// Every op is synchronized, so the final array must be deterministic regardless of carrier
// count; a lost update / lost wakeup / GC remap of the backing int[] changes the weighted
// checksum Sum(arr[i]*(i+1)). Must match real java at carriers 1 & 2 and with GC forced.
public class AtomIntArrStress {
    static final int THREADS = 8;
    static final int ITER = 500;
    static final int SIZE = 10;
    static final AtomicIntegerArray arr = new AtomicIntegerArray(SIZE);

    public static int demo() throws Exception {
        for (int i = 0; i < SIZE; i++) {
            arr.set(i, 0);
        }
        Thread[] ts = new Thread[THREADS];
        for (int i = 0; i < THREADS; i++) {
            ts[i] = new Thread(() -> {
                for (int j = 0; j < ITER; j++) {
                    arr.getAndAdd(j % SIZE, (j % 7) + 1);
                }
                for (int k = 0; k < 50; k++) {
                    while (true) {
                        int cur = arr.get(0);
                        if (arr.compareAndSet(0, cur, cur + 1)) {
                            break;
                        }
                    }
                }
            });
        }
        for (int i = 0; i < THREADS; i++) {
            ts[i].start();
        }
        for (int i = 0; i < THREADS; i++) {
            ts[i].join();
        }
        int acc = 0;
        for (int i = 0; i < SIZE; i++) {
            acc += arr.get(i) * (i + 1);
        }
        return acc;
    }
}
