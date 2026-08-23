package st;

import java.util.ArrayList;

// Concurrent structural mutation of a SHARED ArrayList guarded by a user monitor:
// 8 fibers each add() 250 distinct values inside synchronized(lock){}. Exercises
// monitorenter/monitorexit on a user Object, ArrayList growth (backing-array
// realloc) under the lock, and the moving GC relocating that array while fibers
// are blocked. Distinct values -> any lost add or corrupted element breaks the sum.
// size = 8*250 = 2000; sum = sum_t(250000*t + 31125) = 7,249,000 -> demo() = 7,251,000.
public class SyncListStress {
    static final Object lock = new Object();
    static final ArrayList list = new ArrayList();

    public static int demo() throws Exception {
        Thread[] ts = new Thread[8];
        for (int t = 0; t < 8; t++) {
            final int tid = t;
            ts[t] = new Thread(() -> {
                for (int j = 0; j < 250; j++) {
                    synchronized (lock) {
                        list.add(Integer.valueOf(tid * 1000 + j));
                    }
                }
            });
        }
        for (int i = 0; i < 8; i++) ts[i].start();
        for (int i = 0; i < 8; i++) ts[i].join();

        long sum = 0;
        int size;
        synchronized (lock) {
            size = list.size();
            for (int i = 0; i < size; i++) {
                sum += ((Integer) list.get(i)).intValue();
            }
        }
        return (int) sum + size;
    }
}
