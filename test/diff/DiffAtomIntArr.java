import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * Differential coverage for java.util.concurrent.atomic.AtomicIntegerArray:
 * construction, get/set, add/increment variants, getAndSet, compareAndSet
 * (success + failure), length, and toString. Each method returns a
 * deterministic int checked byte-for-byte vs real java.
 */
public class DiffAtomIntArr {
    static int allZero() {
        AtomicIntegerArray a = new AtomicIntegerArray(5);
        int sum = 0;
        for (int i = 0; i < a.length(); i++) {
            sum += a.get(i);
        }
        return sum; // 0
    }

    static int setGet() {
        AtomicIntegerArray a = new AtomicIntegerArray(5);
        a.set(3, 42);
        return a.get(3); // 42
    }

    static int incTwice() {
        AtomicIntegerArray a = new AtomicIntegerArray(5);
        a.incrementAndGet(2);
        return a.incrementAndGet(2); // 2
    }

    static int addGet() {
        AtomicIntegerArray a = new AtomicIntegerArray(5);
        return a.addAndGet(0, 10); // 10
    }

    static int getAndSetOld() {
        AtomicIntegerArray a = new AtomicIntegerArray(5);
        a.set(1, 7);
        return a.getAndSet(1, 99) * 100 + a.get(1); // 7*100 + 99 = 799
    }

    static int casSuccess() {
        AtomicIntegerArray a = new AtomicIntegerArray(5);
        a.set(4, 5);
        return a.compareAndSet(4, 5, 6) ? 1 : 0; // 1
    }

    static int casFail() {
        AtomicIntegerArray a = new AtomicIntegerArray(5);
        a.set(4, 5);
        return a.compareAndSet(4, 999, 6) ? 1 : 0; // 0
    }

    static int fromArrayCopy() {
        int[] src = {1, 2, 3};
        AtomicIntegerArray a = new AtomicIntegerArray(src);
        src[0] = 100; // must not affect the atomic array (copy)
        return a.get(0) * 100 + a.length(); // 1*100 + 3 = 103
    }

    static int getAddIncDec() {
        AtomicIntegerArray a = new AtomicIntegerArray(3);
        a.set(0, 20);
        int gaa = a.getAndAdd(0, 5); // returns 20, now 25
        int gi = a.getAndIncrement(0); // returns 25, now 26
        int dec = a.decrementAndGet(0); // 25
        return gaa * 10000 + gi * 100 + dec; // 20*10000 + 25*100 + 25 = 202525
    }

    static int lengthCase() {
        return new AtomicIntegerArray(8).length(); // 8
    }

    static int toStr() {
        AtomicIntegerArray a = new AtomicIntegerArray(3);
        a.set(1, 5);
        String s = a.toString(); // "[0, 5, 0]"
        int acc = 0;
        for (int i = 0; i < s.length(); i++) {
            acc += s.charAt(i);
        }
        return acc;
    }
}
