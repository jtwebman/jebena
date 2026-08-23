import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class DiffAtomUpd {
    public static int updAndGet() {
        AtomicInteger a = new AtomicInteger(10);
        return a.updateAndGet(x -> x * 2);
    }

    public static int getAndUpd() {
        AtomicInteger a = new AtomicInteger(10);
        int r = a.getAndUpdate(x -> x + 5);
        return r * 100 + a.get();
    }

    public static int accAndGet() {
        AtomicLong a = new AtomicLong(100L);
        long r = a.accumulateAndGet(7L, (x, y) -> x + y);
        return (int) r;
    }

    public static int getAndAcc() {
        AtomicInteger a = new AtomicInteger(3);
        int r = a.getAndAccumulate(4, (x, y) -> x * y);
        return r * 100 + a.get();
    }

    public static int refUpdAndGet() {
        AtomicReference a = new AtomicReference("a");
        String r = (String) a.updateAndGet(s -> ((String) s) + "b");
        int sum = 0;
        for (int i = 0; i < r.length(); i++) {
            sum = sum * 31 + r.charAt(i);
        }
        return sum;
    }

    public static int refGetAndUpd() {
        AtomicReference a = new AtomicReference("x");
        String r = (String) a.getAndUpdate(s -> ((String) s) + "y");
        String now = (String) a.get();
        return r.length() * 1000 + now.length();
    }

    public static int refAccAndGet() {
        AtomicReference a = new AtomicReference("m");
        String r = (String) a.accumulateAndGet("n", (x, y) -> ((String) x) + ((String) y));
        int sum = 0;
        for (int i = 0; i < r.length(); i++) {
            sum = sum * 31 + r.charAt(i);
        }
        return sum;
    }

    public static int longGetAndUpd() {
        AtomicLong a = new AtomicLong(50L);
        long r = a.getAndUpdate(x -> x * 3);
        return (int) (r + a.get());
    }

    public static int longGetAndAcc() {
        AtomicLong a = new AtomicLong(9L);
        long r = a.getAndAccumulate(5L, (x, y) -> x - y);
        return (int) (r * 100 + a.get());
    }

    public static int intAccMax() {
        AtomicInteger a = new AtomicInteger(7);
        int r = a.accumulateAndGet(12, (x, y) -> x > y ? x : y);
        return r;
    }
}
