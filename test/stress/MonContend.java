package st;

// Monitor-parking scale: 16 fibers each do 500 synchronized(lock){count++} on ONE
// shared lock. 16 contenders >> 4 carriers, so most must PARK on the monitor (a
// deadlock under the old spin model). Correct mutual exclusion => 16*500 = 8000.
public class MonContend {
    static final Object lock = new Object();
    static int count = 0;

    public static int demo() throws Exception {
        count = 0;
        Thread[] ts = new Thread[16];
        for (int i = 0; i < 16; i++) {
            ts[i] = new Thread(() -> {
                for (int j = 0; j < 500; j++) {
                    synchronized (lock) {
                        count++;
                    }
                }
            });
        }
        for (int i = 0; i < 16; i++) ts[i].start();
        for (int i = 0; i < 16; i++) ts[i].join();
        return count;
    }
}
