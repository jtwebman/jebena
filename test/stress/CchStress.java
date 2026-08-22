package st;

import java.util.concurrent.ConcurrentHashMap;

// Concurrent ConcurrentHashMap.merge: 8 fibers each increment counts for 4 shared
// keys, 500 times each. merge must be atomic (read-modify-write under the map lock)
// or updates are lost. Correct total across all keys = 8*500 = 4000; the max single
// key gets 8*500/4 = 1000. Returns total*10 + (maxKeyCount/100) = 40000 + 10 = 40010.
public class CchStress {
    static final ConcurrentHashMap map = new ConcurrentHashMap();

    public static int demo() throws Exception {
        map.clear();
        Thread[] ts = new Thread[8];
        for (int i = 0; i < 8; i++) {
            ts[i] = new Thread(() -> {
                for (int j = 0; j < 500; j++) {
                    String key = "k" + (j % 4);
                    map.merge(key, Integer.valueOf(1), (a, b) -> Integer.valueOf(((Integer) a).intValue() + ((Integer) b).intValue()));
                }
            });
        }
        for (int i = 0; i < 8; i++) ts[i].start();
        for (int i = 0; i < 8; i++) ts[i].join();

        int total = 0;
        int max = 0;
        for (Object v : map.values()) {
            int c = ((Integer) v).intValue();
            total += c;
            if (c > max) {
                max = c;
            }
        }
        return total * 10 + (max / 100); // 4000*10 + 1000/100 = 40010
    }
}
