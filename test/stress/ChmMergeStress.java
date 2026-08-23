package st;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

// ConcurrentHashMap concurrent-merge thread-safety, which also exercises the method-ref
// path (Integer::sum bound to a BiFunction: unbox the two Integer args, box the int result).
// 6 threads each merge(key, 1, Integer::sum) 500 times over a 10-key space (key = j % 10).
// merge() is synchronized (monitor-based, so it blocks rather than spins -> safe under the
// cooperative M:N scheduler; no phasing needed). Every increment is atomic, so no update is
// lost: total = 6 * 500 = 3000, spread evenly as 300 per key over 10 keys. demo() encodes
// sum + keys*10000 (+ a sentinel if any key != 300) = 3000 + 100000 = 103000 deterministically.
// Must match real java at carriers 1 & 2 and with GC forced.
public class ChmMergeStress {
    // Generic here so javac binds `Integer::sum` to merge's BiFunction<Integer,Integer,Integer>;
    // generics erase to the same bytecode jebena runs against jbase's raw ConcurrentHashMap.
    static final ConcurrentHashMap<Integer, Integer> map = new ConcurrentHashMap<>();

    public static int demo() throws Exception {
        map.clear();

        Thread[] t = new Thread[6];
        for (int i = 0; i < 6; i++) {
            t[i] = new Thread(() -> {
                for (int j = 0; j < 500; j++) {
                    map.merge(Integer.valueOf(j % 10), Integer.valueOf(1), Integer::sum);
                }
            });
        }
        for (int i = 0; i < 6; i++) {
            t[i].start();
        }
        for (int i = 0; i < 6; i++) {
            t[i].join();
        }

        int sum = 0;
        int keys = 0;
        boolean allEq = true;
        Iterator it = map.values().iterator();
        while (it.hasNext()) {
            int v = ((Integer) it.next()).intValue();
            sum += v;
            keys++;
            if (v != 300) {
                allEq = false;
            }
        }
        return sum + keys * 10000 + (allEq ? 0 : -1);
    }
}
