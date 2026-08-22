package java.util.concurrent.locks;

// Clean-room Lock (core subset: timed/interruptible variants that need TimeUnit
// are deferred until a real program needs them).
public interface Lock {
    void lock();
    void unlock();
    boolean tryLock();
    Condition newCondition();
}
