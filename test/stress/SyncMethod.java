package st;

// Synchronized-METHOD (ACC_SYNCHRONIZED) mutual-exclusion stress. 8 fibers each
// call inst.bump() 1000x; bump() is a synchronized instance method, so it locks
// `inst`. Without real method monitors the shared non-atomic count races and loses
// updates; with them count == 8000 exactly. Lambda captures nothing (static inst +
// static field).
public class SyncMethod {
    static final SyncMethod inst = new SyncMethod();
    static int count = 0;

    synchronized void bump() {
        count = count + 1;
    }

    public static int demo() throws Exception {
        count = 0;
        Thread[] ts = new Thread[8];
        for (int i = 0; i < 8; i++) {
            ts[i] = new Thread(() -> {
                for (int j = 0; j < 1000; j++) inst.bump();
            });
        }
        for (int i = 0; i < 8; i++) ts[i].start();
        for (int i = 0; i < 8; i++) ts[i].join();
        return count;
    }
}
