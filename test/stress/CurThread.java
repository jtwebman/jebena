package st;

import java.util.concurrent.atomic.AtomicInteger;

// Per-fiber Thread.currentThread(): each of 8 workers must see a Thread distinct
// from the main thread (real java => 8). With a single shared/static currentThread
// it would be 0. Lambda captures nothing (static field + static counter).
public class CurThread {
    static Thread mainThread;
    static final AtomicInteger nonMain = new AtomicInteger(0);

    public static int demo() throws Exception {
        mainThread = Thread.currentThread();
        nonMain.set(0);
        Thread[] ts = new Thread[8];
        for (int i = 0; i < 8; i++) {
            ts[i] = new Thread(() -> {
                if (Thread.currentThread() != mainThread) nonMain.incrementAndGet();
            });
        }
        for (int i = 0; i < 8; i++) ts[i].start();
        for (int i = 0; i < 8; i++) ts[i].join();
        return nonMain.get();
    }
}
