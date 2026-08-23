import java.util.concurrent.atomic.AtomicLongArray;

public class DiffAtomLongArr {
    public static int length() {
        AtomicLongArray a = new AtomicLongArray(7);
        return a.length();
    }

    public static int fromArray() {
        long[] seed = new long[] { 1L, 2L, 3L };
        AtomicLongArray a = new AtomicLongArray(seed);
        seed[0] = 99L; // must not affect the copy
        return (int) (a.get(0) + a.get(1) + a.get(2));
    }

    public static int addAndGetBig() {
        AtomicLongArray a = new AtomicLongArray(3);
        return (int) a.addAndGet(0, 5000000000L);
    }

    public static int getAndAdd() {
        AtomicLongArray a = new AtomicLongArray(3);
        a.set(1, 10L);
        long old = a.getAndAdd(1, 32L);
        return (int) (old * 1000 + a.get(1));
    }

    public static int incrementAndGet() {
        AtomicLongArray a = new AtomicLongArray(2);
        a.set(0, 41L);
        return (int) a.incrementAndGet(0);
    }

    public static int decrementAndGet() {
        AtomicLongArray a = new AtomicLongArray(2);
        a.set(0, 41L);
        return (int) a.decrementAndGet(0);
    }

    public static int getAndSet() {
        AtomicLongArray a = new AtomicLongArray(2);
        a.set(0, 7L);
        long old = a.getAndSet(0, 123L);
        return (int) (old * 1000 + a.get(0));
    }

    public static int casSuccess() {
        AtomicLongArray a = new AtomicLongArray(2);
        a.set(0, 5L);
        boolean ok = a.compareAndSet(0, 5L, 5000000001L);
        return (ok ? 1 : 0) * 1000000 + (int) a.get(0);
    }

    public static int casFail() {
        AtomicLongArray a = new AtomicLongArray(2);
        a.set(0, 5L);
        boolean ok = a.compareAndSet(0, 6L, 999L);
        return (ok ? 1 : 0) * 1000 + (int) a.get(0);
    }

    public static int getAndIncDec() {
        AtomicLongArray a = new AtomicLongArray(1);
        a.set(0, 100L);
        long x = a.getAndIncrement(0); // 100, now 101
        long y = a.getAndDecrement(0); // 101, now 100
        return (int) (x + y + a.get(0));
    }

    public static int toStringCk() {
        AtomicLongArray a = new AtomicLongArray(3);
        a.set(0, 5000000000L);
        a.set(1, -2L);
        a.set(2, 0L);
        String s = a.toString();
        int ck = 0;
        for (int i = 0; i < s.length(); i++) {
            ck = ck * 31 + s.charAt(i);
        }
        return ck;
    }
}
