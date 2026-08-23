package st;

import java.util.concurrent.ConcurrentHashMap;

// ConcurrentHashMap.compute atomicity (distinct from cch-compute-stress which covers
// computeIfAbsent): 8 fibers each do 500 increments cycling over 10 keys via
// compute(key, (k,v) -> v==null ? 1 : v+1). compute() is synchronized, so every key must
// end at exactly 8 * (500/10) = 400 and the weighted checksum Sum(count[i]*(i+1)) = 22000.
// A lost read-modify-write, lost wakeup, or GC remap of the boxed Integer values under
// contention would change the checksum. Must match real java at carriers 1 & 4 (+GC).
public class CchComputeHist {
    static final int THREADS = 8;
    static final int ITER = 500;
    static final int KEYS = 10;
    static final ConcurrentHashMap map = new ConcurrentHashMap();

    public static int demo() throws Exception {
        map.clear();
        Thread[] ts = new Thread[THREADS];
        for (int i = 0; i < THREADS; i++) {
            ts[i] = new Thread(() -> {
                for (int j = 0; j < ITER; j++) {
                    String k = "k" + (j % KEYS);
                    map.compute(k, (kk, v) -> Integer.valueOf(v == null ? 1 : ((Integer) v) + 1));
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
        for (int i = 0; i < KEYS; i++) {
            Integer c = (Integer) map.get("k" + i);
            int cv = (c == null) ? 0 : c.intValue();
            acc += cv * (i + 1);
        }
        return acc; // 400 per key * (1+2+...+10) = 400 * 55 = 22000
    }
}
