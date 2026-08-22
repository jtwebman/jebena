package st;

import java.util.concurrent.atomic.AtomicInteger;
import st.gen.C0;
import st.gen.C1;
import st.gen.C2;
import st.gen.C3;
import st.gen.C4;
import st.gen.C5;
import st.gen.C6;
import st.gen.C7;
import st.gen.C8;
import st.gen.C9;
import st.gen.C10;
import st.gen.C11;

// Concurrent class-loading + interning stress. 8 fibers each call sumAll(), which
// references 12 classes (st.gen.C0..C11) that are loaded LAZILY from a classpath
// directory -- so the fibers race to first-load + first-init + first-intern those
// classes' string literals. The load_lock must serialize that (no duplicate load,
// no torn loader arrays, no double <clinit>) while every fiber still gets the same
// value. Lambda captures nothing (static counter + static method) per tested
// lambda support. Each C_i.v() = "L<i>".length() (==2) + i, so sumAll = sum(2+i,
// i=0..11) = 24 + 66 = 90; x8 fibers = 720.
public class LoadStress {
    static final AtomicInteger total = new AtomicInteger(0);

    static int sumAll() {
        return C0.v() + C1.v() + C2.v() + C3.v() + C4.v() + C5.v()
                + C6.v() + C7.v() + C8.v() + C9.v() + C10.v() + C11.v();
    }

    public static int demo() throws Exception {
        total.set(0);
        Thread[] ts = new Thread[8];
        for (int i = 0; i < 8; i++) {
            ts[i] = new Thread(() -> {
                total.addAndGet(sumAll());
            });
        }
        for (int i = 0; i < 8; i++) ts[i].start();
        for (int i = 0; i < 8; i++) ts[i].join();
        return total.get();
    }
}
