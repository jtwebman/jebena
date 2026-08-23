import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Iterator;

public class DiffCLQ {
    public static int fifoChecksum() {
        ConcurrentLinkedQueue q = new ConcurrentLinkedQueue();
        for (int i = 1; i <= 5; i++) q.offer(Integer.valueOf(i));
        int acc = 0;
        while (!q.isEmpty()) {
            Integer v = (Integer) q.poll();
            acc = acc * 31 + v.intValue();
        }
        return acc;
    }

    public static int peekAfterOffers() {
        ConcurrentLinkedQueue q = new ConcurrentLinkedQueue();
        for (int i = 1; i <= 5; i++) q.offer(Integer.valueOf(i));
        Integer p = (Integer) q.peek();
        // peek must not remove
        return p.intValue() * 100 + q.size();
    }

    public static int sizeIsEmptyTransitions() {
        ConcurrentLinkedQueue q = new ConcurrentLinkedQueue();
        int acc = q.isEmpty() ? 1 : 0;          // 1
        q.add(Integer.valueOf(10));
        q.add(Integer.valueOf(20));
        acc = acc * 10 + q.size();               // 12
        acc = acc * 10 + (q.isEmpty() ? 1 : 0);  // 120
        q.poll();
        acc = acc * 10 + q.size();               // 1201
        q.poll();
        acc = acc * 10 + (q.isEmpty() ? 1 : 0);  // 12011
        return acc;
    }

    public static int removeMiddleThenOrder() {
        ConcurrentLinkedQueue q = new ConcurrentLinkedQueue();
        for (int i = 1; i <= 5; i++) q.offer(Integer.valueOf(i));
        boolean removed = q.remove(Integer.valueOf(3));
        int acc = removed ? 9 : 0;
        while (!q.isEmpty()) {
            Integer v = (Integer) q.poll();
            acc = acc * 31 + v.intValue();
        }
        return acc;
    }

    public static int removeHeadAndTail() {
        ConcurrentLinkedQueue q = new ConcurrentLinkedQueue();
        for (int i = 1; i <= 4; i++) q.offer(Integer.valueOf(i));
        boolean rh = q.remove(Integer.valueOf(1)); // head
        boolean rt = q.remove(Integer.valueOf(4)); // tail
        boolean rm = q.remove(Integer.valueOf(99)); // miss
        int acc = (rh ? 1 : 0) * 100 + (rt ? 1 : 0) * 10 + (rm ? 1 : 0);
        acc = acc * 10 + q.size();
        // remaining should be 2,3 in order
        Integer a = (Integer) q.poll();
        Integer b = (Integer) q.poll();
        return acc * 100 + a.intValue() * 10 + b.intValue();
    }

    public static int containsHitMiss() {
        ConcurrentLinkedQueue q = new ConcurrentLinkedQueue();
        for (int i = 1; i <= 5; i++) q.offer(Integer.valueOf(i));
        int acc = 0;
        acc = acc * 10 + (q.contains(Integer.valueOf(1)) ? 1 : 0);
        acc = acc * 10 + (q.contains(Integer.valueOf(5)) ? 1 : 0);
        acc = acc * 10 + (q.contains(Integer.valueOf(3)) ? 1 : 0);
        acc = acc * 10 + (q.contains(Integer.valueOf(6)) ? 1 : 0);
        acc = acc * 10 + (q.contains(null) ? 1 : 0);
        return acc; // expect 11110
    }

    public static int pollEmpty() {
        ConcurrentLinkedQueue q = new ConcurrentLinkedQueue();
        Object o = q.poll();
        if (o != null) return 0;
        q.offer(Integer.valueOf(7));
        q.poll();
        Object o2 = q.poll();
        return o2 == null ? -1 : 0;
    }

    public static int iteratorOrder() {
        ConcurrentLinkedQueue q = new ConcurrentLinkedQueue();
        for (int i = 1; i <= 5; i++) q.offer(Integer.valueOf(i));
        int acc = 0;
        Iterator it = q.iterator();
        while (it.hasNext()) {
            Integer v = (Integer) it.next();
            acc = acc * 31 + v.intValue();
        }
        // queue unchanged by iteration
        return acc * 10 + q.size();
    }

    public static int toStringHash() {
        ConcurrentLinkedQueue q = new ConcurrentLinkedQueue();
        for (int i = 1; i <= 3; i++) q.offer(Integer.valueOf(i));
        String s = q.toString(); // "[1, 2, 3]"
        int h = s.hashCode();
        ConcurrentLinkedQueue e = new ConcurrentLinkedQueue();
        int he = e.toString().hashCode(); // "[]"
        return h ^ he;
    }
}
