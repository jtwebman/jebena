package java.util.concurrent;

/** Clean-room java.util.concurrent.Executor. */
public interface Executor {
    void execute(Runnable command);
}
