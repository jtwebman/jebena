package java.util.concurrent;

/** Clean-room minimal java.util.concurrent.ExecutorService. */
public interface ExecutorService extends Executor {
    Future submit(Callable task);

    Future submit(Runnable task);

    void shutdown();
}
