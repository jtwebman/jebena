package st;

import java.util.concurrent.atomic.AtomicInteger;

// wait-parking scale: 16 fibers each wait() on ONE lock until a flag is set; main
// sets the flag and notifyAll(). 16 waiters >> 4 carriers, so all must PARK (not
// spin, which would deadlock). Each woken waiter adds 1 => 16.
public class ManyWait {
    static final Object lock = new Object();
    static final AtomicInteger woke = new AtomicInteger(0);
    static int go = 0;
    static int ready = 0;

    public static int demo() throws Exception {
        woke.set(0);
        go = 0;
        ready = 0;
        Thread[] ts = new Thread[16];
        for (int i = 0; i < 16; i++) {
            ts[i] = new Thread(() -> {
                synchronized (lock) {
                    ready++;
                    lock.notifyAll();
                    while (go == 0) {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                        }
                    }
                }
                woke.incrementAndGet();
            });
        }
        for (int i = 0; i < 16; i++) ts[i].start();
        synchronized (lock) {
            while (ready < 16) lock.wait();
            go = 1;
            lock.notifyAll();
        }
        for (int i = 0; i < 16; i++) ts[i].join();
        return woke.get();
    }
}
