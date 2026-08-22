package st;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Fiber-error isolation stress. 8 worker threads: worker 0 recurses infinitely
 * (jebena: a VM CallDepthExceeded RunError; real java: StackOverflowError) so its
 * run() dies with an UNCAUGHT error; workers 1..7 each add (k+1) to a shared total.
 * main joins all 8 and returns the total.
 *
 * A single thread's uncaught error must terminate ONLY that thread (Java Thread
 * semantics) -- it must NOT kill the carrier running it (which would deadlock a
 * concurrent GC via the safepoint protocol) and must NOT fail the whole program.
 * So the other 7 workers complete and total = 2+3+...+8 = 35, matching real java.
 * Runs at carriers 1 & 4 (+GC) to prove no hang and correct isolation.
 */
public class InjectErr {
    static final AtomicLong total = new AtomicLong(0);

    // Non-tail-recursive (the + x prevents TCO) -> unbounded stack -> error.
    static int recurse(int x) {
        return recurse(x + 1) + x;
    }

    static final class W extends Thread {
        final int k;

        W(int k) {
            this.k = k;
        }

        public void run() {
            if (k == 0) {
                recurse(0); // uncaught VM/stack error: this thread dies, nothing added
                return;
            }
            total.addAndGet(k + 1);
        }
    }

    public static int demo() {
        total.set(0);
        W[] w = new W[8];
        for (int i = 0; i < 8; i++) {
            w[i] = new W(i);
            w[i].start();
        }
        for (int i = 0; i < 8; i++) {
            try {
                w[i].join();
            } catch (InterruptedException e) {
                // ignore
            }
        }
        return (int) total.get(); // worker 0 errored -> 2+3+...+8 = 35
    }
}
