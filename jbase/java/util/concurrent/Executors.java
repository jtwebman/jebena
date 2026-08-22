package java.util.concurrent;

/** Clean-room minimal java.util.concurrent.Executors factory. */
public class Executors {
    private Executors() {
    }

    public static ExecutorService newFixedThreadPool(int nThreads) {
        return new ThreadPoolExecutor(nThreads);
    }

    public static ExecutorService newSingleThreadExecutor() {
        return new ThreadPoolExecutor(1);
    }
}
