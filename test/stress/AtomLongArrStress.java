package st;

import java.util.concurrent.atomic.AtomicLongArray;

// AtomicLongArray 64-bit contention: 8 fibers each do 500 getAndAdd across a shared
// 10-element array (index j%10, delta (j%7+1)*1_000_000_000L to exercise the full 64 bits)
// then 50 compareAndSet-spin increments on index 0. Every op is synchronized, so the final
// array must be deterministic regardless of carrier count; a lost update / lost wakeup / GC
// remap of the backing long[] changes the weighted checksum (int) Sum(arr[i]*(i+1)). Must
// match real java at carriers 1 & 2 and with GC forced.
public class AtomLongArrStress {
    static final int THREADS = 8;
    static final int ITER = 500;
    static final int SIZE = 10;
    static final AtomicLongArray arr = new AtomicLongArray(SIZE);

    public static int demo() throws Exception {
        for (int i = 0; i < SIZE; i++) {
            arr.set(i, 0L);
        }
        Thread[] ts = new Thread[THREADS];
        for (int i = 0; i < THREADS; i++) {
            ts[i] = new Thread(() -> {
                for (int j = 0; j < ITER; j++) {
                    arr.getAndAdd(j % SIZE, ((j % 7) + 1) * 1000000000L);
                }
                for (int k = 0; k < 50; k++) {
                    while (true) {
                        long cur = arr.get(0);
                        if (arr.compareAndSet(0, cur, cur + 1L)) {
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
        long acc = 0L;
        for (int i = 0; i < SIZE; i++) {
            acc += arr.get(i) * (i + 1);
        }
        return (int) acc;
    }
}
