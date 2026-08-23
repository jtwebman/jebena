package st;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

// Treiber lock-free stack over AtomicReference.compareAndSet: phase 1, 8 fibers each
// push 250 DISTINCT values (CAS the head); phase 2, 8 fibers pop everything (CAS the
// head) summing into an AtomicLong. Exercises reference-CAS under contention AND under
// the moving GC (the head/next reference ids are relocated while fibers spin on CAS).
// A non-atomic CAS loses pushes/pops. total pushed = 2000, sum = 7,251,000 ->
// demo() = sum + popped = 7,253,000.
public class TreiberStress {
    static final class Node {
        final int v;
        Node next;

        Node(int v) {
            this.v = v;
        }
    }

    static final AtomicReference head = new AtomicReference();
    static final AtomicLong sum = new AtomicLong(0);
    static final AtomicLong popped = new AtomicLong(0);

    static void push(int v) {
        Node n = new Node(v);
        while (true) {
            Node old = (Node) head.get();
            n.next = old;
            if (head.compareAndSet(old, n)) {
                return;
            }
        }
    }

    static Node pop() {
        while (true) {
            Node old = (Node) head.get();
            if (old == null) {
                return null;
            }
            Node nx = old.next;
            if (head.compareAndSet(old, nx)) {
                return old;
            }
        }
    }

    public static int demo() throws Exception {
        Thread[] pu = new Thread[8];
        for (int i = 0; i < 8; i++) {
            final int pid = i;
            pu[i] = new Thread(() -> {
                for (int j = 1; j <= 250; j++) {
                    push(pid * 1000 + j);
                }
            });
        }
        for (int i = 0; i < 8; i++) pu[i].start();
        for (int i = 0; i < 8; i++) pu[i].join();

        Thread[] po = new Thread[8];
        for (int i = 0; i < 8; i++) {
            po[i] = new Thread(() -> {
                Node n;
                while ((n = pop()) != null) {
                    sum.addAndGet(n.v);
                    popped.incrementAndGet();
                }
            });
        }
        for (int i = 0; i < 8; i++) po[i].start();
        for (int i = 0; i < 8; i++) po[i].join();

        return (int) sum.get() + (int) popped.get();
    }
}
