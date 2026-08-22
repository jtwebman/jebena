package st;

// wait/notify + monitor handoff. 8 worker fibers each, under synchronized(lock),
// bump `done`, decrement `remaining`, and notifyAll(); main waits under the same
// monitor until remaining==0. Exercises: wait() releasing the monitor so workers
// can enter, notifyAll() waking the waiter, the waiter reacquiring, and mutual
// exclusion of the shared non-atomic counters. Deterministic result: done==8.
public class WaitNotify {
    static final Object lock = new Object();
    static int remaining = 0;
    static int done = 0;

    public static int demo() throws Exception {
        remaining = 8;
        done = 0;
        Thread[] ws = new Thread[8];
        for (int i = 0; i < 8; i++) {
            ws[i] = new Thread(() -> {
                synchronized (lock) {
                    done += 1;
                    remaining -= 1;
                    lock.notifyAll();
                }
            });
        }
        for (int i = 0; i < 8; i++) ws[i].start();
        synchronized (lock) {
            while (remaining > 0) {
                lock.wait();
            }
        }
        for (int i = 0; i < 8; i++) ws[i].join();
        return done;
    }
}
