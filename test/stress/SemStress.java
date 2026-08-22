package st;

import java.util.concurrent.Semaphore;

// Semaphore(1) used as a mutex around a non-atomic counter: 8 fibers x500. Correct
// mutual exclusion => 4000; a broken semaphore loses updates.
public class SemStress {
    static final Semaphore sem = new Semaphore(1);
    static int count = 0;

    public static int demo() throws Exception {
        count = 0;
        Thread[] ts = new Thread[8];
        for (int i = 0; i < 8; i++) {
            ts[i] = new Thread(() -> {
                for (int j = 0; j < 500; j++) {
                    try {
                        sem.acquire();
                        count++;
                        sem.release();
                    } catch (InterruptedException e) {
                    }
                }
            });
        }
        for (int i = 0; i < 8; i++) ts[i].start();
        for (int i = 0; i < 8; i++) ts[i].join();
        return count;
    }
}
