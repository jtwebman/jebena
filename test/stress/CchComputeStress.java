package st;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// ConcurrentHashMap.computeIfAbsent atomicity: 8 fibers x 500 iterations each do
// computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet() over 5 shared
// keys. computeIfAbsent must create exactly ONE AtomicInteger per key even under
// concurrency (synchronized on the map); the counters are AtomicInteger so the
// increments don't lose. Total increments = 8*500 = 4000 across 5 keys ->
// demo() = total*10 + keys = 40000 + 5 = 40005.
public class CchComputeStress {
    static final ConcurrentHashMap map = new ConcurrentHashMap();

    public static int demo() throws Exception {
        map.clear();
        Thread[] ts = new Thread[8];
        for (int i = 0; i < 8; i++) {
            ts[i] = new Thread(() -> {
                for (int j = 0; j < 500; j++) {
                    String key = "k" + (j % 5);
                    AtomicInteger ctr = (AtomicInteger) map.computeIfAbsent(key, k -> new AtomicInteger(0));
                    ctr.incrementAndGet();
                }
            });
        }
        for (int i = 0; i < 8; i++) ts[i].start();
        for (int i = 0; i < 8; i++) ts[i].join();

        int total = 0;
        int keys = 0;
        for (Object v : map.values()) {
            total += ((AtomicInteger) v).get();
            keys++;
        }
        return total * 10 + keys; // 40005
    }
}
