package st;

// Monitor (synchronized) mutual-exclusion stress. 8 fibers each do
// `synchronized (lock) { count++; }` 1000 times on a shared NON-atomic static int.
// Without real monitors the getstatic/iadd/putstatic sequence interleaves across
// carriers and loses updates (count < 8000); with reentrant per-object monitors
// the block is mutually exclusive and count == 8000 exactly. Lambda captures
// nothing (static lock + static field) per tested lambda support.
public class SyncCounter {
    static final Object lock = new Object();
    static int count = 0;

    public static int demo() throws Exception {
        count = 0;
        Thread[] ts = new Thread[8];
        for (int i = 0; i < 8; i++) {
            ts[i] = new Thread(() -> {
                for (int j = 0; j < 1000; j++) {
                    synchronized (lock) {
                        count++;
                    }
                }
            });
        }
        for (int i = 0; i < 8; i++) ts[i].start();
        for (int i = 0; i < 8; i++) ts[i].join();
        return count;
    }
}
