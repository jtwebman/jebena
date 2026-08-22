package java.util.concurrent;

/**
 * Clean-room minimal fixed-size ThreadPoolExecutor: N worker threads (fibers) pull
 * tasks from a LinkedBlockingQueue and run them. submit() wraps the task in a
 * FutureTask and enqueues it; the returned Future.get() parks until it runs.
 * shutdown() enqueues one POISON sentinel per worker so each exits after draining.
 * Because take()/put()/get() all PARK, the whole pool runs at carriers=1.
 */
public class ThreadPoolExecutor implements ExecutorService {
    static final Runnable POISON = new Runnable() {
        public void run() {
        }
    };

    private final LinkedBlockingQueue queue = new LinkedBlockingQueue();
    private final Thread[] workers;
    private volatile boolean isShutdown;

    public ThreadPoolExecutor(int nThreads) {
        if (nThreads <= 0) {
            throw new IllegalArgumentException("nThreads <= 0");
        }
        workers = new Thread[nThreads];
        for (int i = 0; i < nThreads; i++) {
            workers[i] = new Thread(new Worker(queue));
            workers[i].start();
        }
    }

    static final class Worker implements Runnable {
        private final LinkedBlockingQueue q;

        Worker(LinkedBlockingQueue q) {
            this.q = q;
        }

        public void run() {
            try {
                while (true) {
                    Object o = q.take();
                    if (o == POISON) {
                        break;
                    }
                    ((Runnable) o).run();
                }
            } catch (InterruptedException e) {
                // exit
            }
        }
    }

    public void execute(Runnable task) {
        if (isShutdown) {
            throw new IllegalStateException("shutdown");
        }
        try {
            queue.put(task);
        } catch (InterruptedException e) {
            throw new RuntimeException("interrupted");
        }
    }

    public Future submit(Callable task) {
        FutureTask ft = new FutureTask(task);
        execute(ft);
        return ft;
    }

    public Future submit(Runnable task) {
        FutureTask ft = new FutureTask(task);
        execute(ft);
        return ft;
    }

    public void shutdown() {
        isShutdown = true;
        for (int i = 0; i < workers.length; i++) {
            try {
                queue.put(POISON);
            } catch (InterruptedException e) {
                // best effort
            }
        }
    }

    /** Join all worker threads (they exit once shutdown's POISON drains through). */
    public void awaitTermination() {
        for (int i = 0; i < workers.length; i++) {
            workers[i].join();
        }
    }
}
