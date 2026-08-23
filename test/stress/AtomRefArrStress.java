package st;

import java.util.concurrent.atomic.AtomicReferenceArray;

// AtomicReferenceArray CAS contention on boxed Integers: 8 fibers each do 500 CAS-loop
// increments across a shared 10-slot array (idx j%10; read cur, compareAndSet cur ->
// Integer(cur+1), retry on failure), so every slot must reach 8*(500/10)=400. compareAndSet
// is synchronized, so the weighted checksum Sum(val[i]*(i+1)) = 400*55 = 22000 is deterministic
// across carriers; a lost CAS / lost wakeup / GC remap of the Object[] or boxed values would
// change it. Must match real java at carriers 1 & 2 and with GC forced.
public class AtomRefArrStress {
    static final int THREADS = 8;
    static final int ITER = 500;
    static final int SIZE = 10;
    static final AtomicReferenceArray arr = new AtomicReferenceArray(SIZE);

    public static int demo() throws Exception {
        for (int i = 0; i < SIZE; i++) {
            arr.set(i, Integer.valueOf(0));
        }
        Thread[] ts = new Thread[THREADS];
        for (int i = 0; i < THREADS; i++) {
            ts[i] = new Thread(() -> {
                for (int j = 0; j < ITER; j++) {
                    int idx = j % SIZE;
                    while (true) {
                        Integer cur = (Integer) arr.get(idx);
                        if (arr.compareAndSet(idx, cur, Integer.valueOf(cur.intValue() + 1))) {
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
            acc += ((Integer) arr.get(i)).intValue() * (i + 1);
        }
        return acc; // 400 per slot * (1+2+...+10) = 400 * 55 = 22000
    }
}
