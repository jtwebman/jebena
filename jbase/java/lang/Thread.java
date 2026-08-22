package java.lang;

// Clean-room java.lang.Thread — SINGLE-THREADED model. Jebena is currently a
// single-threaded VM: Thread.start() runs the target's run() synchronously on
// the calling thread, so program results are deterministic and differentially
// testable. join()/sleep() are no-ops, isAlive() is true only during run().
// Real preemptive OS-thread parallelism is a deliberate later iteration.
public class Thread implements Runnable {
    private Runnable target;
    private String name;
    private boolean daemon;
    private int priority = 5;
    private boolean started;
    private boolean alive;

    private static int autoNumber = 0;
    private static Thread currentThread = new Thread("main");

    public Thread() {
        this(null, nextName());
    }

    public Thread(Runnable target) {
        this(target, nextName());
    }

    public Thread(String name) {
        this(null, name);
    }

    public Thread(Runnable target, String name) {
        this.target = target;
        this.name = name;
    }

    private static synchronized String nextName() {
        return "Thread-" + (autoNumber++);
    }

    public void run() {
        if (target != null) {
            target.run();
        }
    }

    public synchronized void start() {
        if (started) {
            throw new IllegalThreadStateException();
        }
        started = true;
        alive = true;
        Thread previous = currentThread;
        currentThread = this;
        try {
            run();
        } finally {
            currentThread = previous;
            alive = false;
        }
    }

    public final void join() {
        // No-op: start() already ran to completion synchronously.
    }

    public final void join(long millis) {
    }

    public final boolean isAlive() {
        return alive;
    }

    public final void setName(String name) {
        this.name = name;
    }

    public final String getName() {
        return name;
    }

    public final void setDaemon(boolean on) {
        this.daemon = on;
    }

    public final boolean isDaemon() {
        return daemon;
    }

    public final void setPriority(int newPriority) {
        this.priority = newPriority;
    }

    public final int getPriority() {
        return priority;
    }

    public static Thread currentThread() {
        return currentThread;
    }

    public static void sleep(long millis) {
        // Single-threaded model: returns immediately.
    }

    public static void yield() {
    }
}
