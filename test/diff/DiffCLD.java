import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.Iterator;

public class DiffCLD {
    public static int offerLastPollFirstFifo() {
        ConcurrentLinkedDeque d = new ConcurrentLinkedDeque();
        for (int i = 1; i <= 5; i++) d.offerLast(Integer.valueOf(i));
        int acc = 0;
        while (!d.isEmpty()) {
            Integer v = (Integer) d.pollFirst();
            acc = acc * 31 + v.intValue();
        }
        return acc;
    }

    public static int offerFirstPollFirstLifo() {
        ConcurrentLinkedDeque d = new ConcurrentLinkedDeque();
        for (int i = 1; i <= 5; i++) d.offerFirst(Integer.valueOf(i));
        int acc = 0;
        while (!d.isEmpty()) {
            Integer v = (Integer) d.pollFirst();
            acc = acc * 31 + v.intValue();
        }
        return acc;
    }

    public static int offerLastPollLastLifo() {
        ConcurrentLinkedDeque d = new ConcurrentLinkedDeque();
        for (int i = 1; i <= 5; i++) d.offerLast(Integer.valueOf(i));
        int acc = 0;
        while (!d.isEmpty()) {
            Integer v = (Integer) d.pollLast();
            acc = acc * 31 + v.intValue();
        }
        return acc;
    }

    public static int peekFirstLast() {
        ConcurrentLinkedDeque d = new ConcurrentLinkedDeque();
        for (int i = 1; i <= 5; i++) d.offerLast(Integer.valueOf(i));
        Integer f = (Integer) d.peekFirst();
        Integer l = (Integer) d.peekLast();
        // peek must not remove
        return f.intValue() * 10000 + l.intValue() * 100 + d.size();
    }

    public static int addFirstAddLastSize() {
        ConcurrentLinkedDeque d = new ConcurrentLinkedDeque();
        d.addLast(Integer.valueOf(2));
        d.addLast(Integer.valueOf(3));
        d.addFirst(Integer.valueOf(1));
        d.addFirst(Integer.valueOf(0));
        int acc = d.size();
        while (!d.isEmpty()) {
            Integer v = (Integer) d.pollFirst();
            acc = acc * 31 + v.intValue();
        }
        return acc;
    }

    public static int descendingIteratorChecksum() {
        ConcurrentLinkedDeque d = new ConcurrentLinkedDeque();
        for (int i = 1; i <= 5; i++) d.offerLast(Integer.valueOf(i));
        int acc = 0;
        Iterator it = d.descendingIterator();
        while (it.hasNext()) {
            Integer v = (Integer) it.next();
            acc = acc * 31 + v.intValue();
        }
        // deque unchanged by iteration
        return acc * 10 + d.size();
    }

    public static int iteratorForwardChecksum() {
        ConcurrentLinkedDeque d = new ConcurrentLinkedDeque();
        for (int i = 1; i <= 5; i++) d.offerLast(Integer.valueOf(i));
        int acc = 0;
        Iterator it = d.iterator();
        while (it.hasNext()) {
            Integer v = (Integer) it.next();
            acc = acc * 31 + v.intValue();
        }
        return acc * 10 + d.size();
    }

    public static int pollEmpty() {
        ConcurrentLinkedDeque d = new ConcurrentLinkedDeque();
        Object a = d.pollFirst();
        Object b = d.pollLast();
        Object c = d.poll();
        if (a != null || b != null || c != null) return 0;
        d.offerLast(Integer.valueOf(7));
        d.pollFirst();
        Object e = d.pollFirst();
        return e == null ? -1 : 0;
    }

    public static int containsHitMiss() {
        ConcurrentLinkedDeque d = new ConcurrentLinkedDeque();
        for (int i = 1; i <= 5; i++) d.offerLast(Integer.valueOf(i));
        int acc = 0;
        acc = acc * 10 + (d.contains(Integer.valueOf(1)) ? 1 : 0);
        acc = acc * 10 + (d.contains(Integer.valueOf(5)) ? 1 : 0);
        acc = acc * 10 + (d.contains(Integer.valueOf(3)) ? 1 : 0);
        acc = acc * 10 + (d.contains(Integer.valueOf(6)) ? 1 : 0);
        acc = acc * 10 + (d.contains(null) ? 1 : 0);
        return acc; // expect 11110
    }

    public static int removeFirstOccurrence() {
        ConcurrentLinkedDeque d = new ConcurrentLinkedDeque();
        d.offerLast(Integer.valueOf(1));
        d.offerLast(Integer.valueOf(2));
        d.offerLast(Integer.valueOf(3));
        d.offerLast(Integer.valueOf(2));
        d.offerLast(Integer.valueOf(4));
        boolean r1 = d.removeFirstOccurrence(Integer.valueOf(2));
        boolean r2 = d.removeFirstOccurrence(Integer.valueOf(99));
        int acc = (r1 ? 1 : 0) * 10 + (r2 ? 1 : 0);
        acc = acc * 10 + d.size();
        // remaining should be 1,3,2,4 in order
        while (!d.isEmpty()) {
            Integer v = (Integer) d.pollFirst();
            acc = acc * 31 + v.intValue();
        }
        return acc;
    }

    public static int removeLastOccurrence() {
        ConcurrentLinkedDeque d = new ConcurrentLinkedDeque();
        d.offerLast(Integer.valueOf(1));
        d.offerLast(Integer.valueOf(2));
        d.offerLast(Integer.valueOf(3));
        d.offerLast(Integer.valueOf(2));
        d.offerLast(Integer.valueOf(4));
        boolean r1 = d.removeLastOccurrence(Integer.valueOf(2));
        int acc = (r1 ? 1 : 0);
        acc = acc * 10 + d.size();
        // remaining should be 1,2,3,4 in order
        while (!d.isEmpty()) {
            Integer v = (Integer) d.pollFirst();
            acc = acc * 31 + v.intValue();
        }
        return acc;
    }

    public static int pushPopStack() {
        ConcurrentLinkedDeque d = new ConcurrentLinkedDeque();
        for (int i = 1; i <= 5; i++) d.push(Integer.valueOf(i));
        int acc = 0;
        while (!d.isEmpty()) {
            Integer v = (Integer) d.pop();
            acc = acc * 31 + v.intValue();
        }
        return acc;
    }
}
