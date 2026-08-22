package java.lang;

// Clean-room java.lang.Thread — GREEN-THREAD model. Thread.start() spawns a
// scheduler fiber (native start0) that runs run(); join() blocks the caller
// until the fiber completes (native join0 pumps the cooperative scheduler).
// currentThread() returns the main Thread for now (per-fiber Thread wiring is a
// later refinement). Single carrier, cooperative — deterministic scheduling.
public class Thread implements Runnable {
    private Runnable target;
    private String name;
    private boolean daemon;
    private int priority = 5;
    private boolean started;
    private long fiberId = -1;

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
        start0();
    }

    private native void start0();

    public final void join() {
        join0();
    }

    public final void join(long millis) {
        join0();
    }

    private native void join0();

    public final native boolean isAlive();

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
        // Cooperative single carrier: deterministic no-op (real timers: stage 4).
    }

    public static void yield() {
    }
}
