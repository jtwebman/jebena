package st;

import java.util.concurrent.ConcurrentHashMap;

// Concurrent histogram over a shared ConcurrentHashMap via merge(key, 1, (a,b)->a+b):
// 8 fibers each do 500 increments cycling over 10 keys (k0..k9), so every key must end
// at exactly 8 * (500/10) = 400. merge() is synchronized, so a correct run yields a
// deterministic weighted checksum Sum(count[i]*(i+1)) = 400*55 = 22000. A lost update
// (non-atomic read-modify-write), a lost wakeup, or a GC remap of the boxed Integer
// values under contention would change some count and thus the checksum. Must match real
// java at carriers 1 & 4 and with GC forced (moving collector relocates map nodes +
// boxed values while fibers contend on merge).
public class HistStress {
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
                    map.merge(k, Integer.valueOf(1),
                            (a, b) -> Integer.valueOf(((Integer) a) + ((Integer) b)));
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
