package java.util.concurrent;

/**
 * Clean-room minimal FutureTask: runs a Callable (or Runnable) once, stores the
 * result, and lets get() block until it completes. Completion is published under
 * the instance monitor; get() wait()s on it, so a fiber calling get() PARKS until
 * the task's worker fiber finishes -- works at carriers=1.
 */
public class FutureTask implements Runnable, Future {
    private final Callable callable;
    private Object result;
    private Throwable exc;
    private boolean done;

    public FutureTask(Callable c) {
        this.callable = c;
    }

    public FutureTask(Runnable r) {
        this.callable = new RunnableAdapter(r);
    }

    static final class RunnableAdapter implements Callable {
        private final Runnable r;

        RunnableAdapter(Runnable r) {
            this.r = r;
        }

        public Object call() {
            r.run();
            return null;
        }
    }

    public void run() {
        Object r = null;
        Throwable e = null;
        try {
            r = callable.call();
        } catch (Throwable t) {
            e = t;
        }
        synchronized (this) {
            result = r;
            exc = e;
            done = true;
            this.notifyAll();
        }
    }

    public Object get() throws Exception {
        synchronized (this) {
            while (!done) {
                this.wait();
            }
        }
        if (exc != null) {
            if (exc instanceof Exception) {
                throw (Exception) exc;
            }
            throw new RuntimeException("task failed: " + exc);
        }
        return result;
    }

    public synchronized boolean isDone() {
        return done;
    }
}
