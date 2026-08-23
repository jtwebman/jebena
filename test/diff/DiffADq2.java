import java.util.ArrayDeque;
import java.util.Iterator;

public class DiffADq2 {
    public static int descendingChecksum() {
        ArrayDeque d = new ArrayDeque();
        for (int i = 1; i <= 6; i++) d.addLast(Integer.valueOf(i));
        Iterator it = d.descendingIterator();
        int acc = 0;
        while (it.hasNext()) {
            Integer v = (Integer) it.next();
            acc = acc * 31 + v.intValue();
        }
        return acc;
    }

    public static int descendingCount() {
        ArrayDeque d = new ArrayDeque();
        for (int i = 0; i < 5; i++) d.addFirst(Integer.valueOf(i));
        Iterator it = d.descendingIterator();
        int n = 0;
        int last = -1;
        while (it.hasNext()) {
            last = ((Integer) it.next()).intValue();
            n++;
        }
        return n * 100 + last;
    }

    public static int removeFirstOccurrence() {
        ArrayDeque d = new ArrayDeque();
        int[] vals = {1, 2, 3, 2, 4, 2};
        for (int v : vals) d.addLast(Integer.valueOf(v));
        boolean r = d.removeFirstOccurrence(Integer.valueOf(2));
        int acc = r ? 7 : 0;
        Iterator it = d.iterator();
        while (it.hasNext()) {
            acc = acc * 31 + ((Integer) it.next()).intValue();
        }
        return acc;
    }

    public static int removeLastOccurrence() {
        ArrayDeque d = new ArrayDeque();
        int[] vals = {1, 2, 3, 2, 4, 2};
        for (int v : vals) d.addLast(Integer.valueOf(v));
        boolean r = d.removeLastOccurrence(Integer.valueOf(2));
        int acc = r ? 7 : 0;
        Iterator it = d.iterator();
        while (it.hasNext()) {
            acc = acc * 31 + ((Integer) it.next()).intValue();
        }
        return acc;
    }

    public static int removeOccurrenceMiss() {
        ArrayDeque d = new ArrayDeque();
        for (int i = 1; i <= 4; i++) d.addLast(Integer.valueOf(i));
        boolean rf = d.removeFirstOccurrence(Integer.valueOf(99));
        boolean rl = d.removeLastOccurrence(Integer.valueOf(99));
        int acc = (rf ? 1 : 0) * 10 + (rl ? 1 : 0);
        return acc * 100 + d.size();
    }

    public static int peekAndGet() {
        ArrayDeque d = new ArrayDeque();
        for (int i = 10; i <= 40; i += 10) d.addLast(Integer.valueOf(i));
        int pf = ((Integer) d.peekFirst()).intValue();
        int pl = ((Integer) d.peekLast()).intValue();
        int gf = ((Integer) d.getFirst()).intValue();
        int gl = ((Integer) d.getLast()).intValue();
        int el = ((Integer) d.element()).intValue();
        int acc = pf;
        acc = acc * 100 + pl;
        acc = acc * 100 + gf;
        acc = acc * 100 + gl;
        acc = acc * 100 + el;
        acc = acc * 10 + d.size();
        return acc;
    }

    public static int peekEmptyVsGetEmpty() {
        ArrayDeque d = new ArrayDeque();
        int acc = (d.peekFirst() == null) ? 1 : 0;
        acc = acc * 10 + ((d.peekLast() == null) ? 1 : 0);
        int caughtGetFirst = 0;
        try {
            d.getFirst();
        } catch (java.util.NoSuchElementException e) {
            caughtGetFirst = 1;
        }
        int caughtGetLast = 0;
        try {
            d.getLast();
        } catch (java.util.NoSuchElementException e) {
            caughtGetLast = 1;
        }
        int caughtElement = 0;
        try {
            d.element();
        } catch (java.util.NoSuchElementException e) {
            caughtElement = 1;
        }
        acc = acc * 10 + caughtGetFirst;
        acc = acc * 10 + caughtGetLast;
        acc = acc * 10 + caughtElement;
        return acc;
    }

    public static int addFirstGrowthOrder() {
        ArrayDeque d = new ArrayDeque();
        // default capacity 16, forcing growth needs >15 adds
        for (int i = 1; i <= 40; i++) d.addFirst(Integer.valueOf(i));
        int acc = d.size();
        Iterator it = d.iterator();
        while (it.hasNext()) {
            acc = acc * 31 + ((Integer) it.next()).intValue();
        }
        return acc;
    }

    public static int addFirstGrowthEndpoints() {
        ArrayDeque d = new ArrayDeque();
        for (int i = 1; i <= 40; i++) d.addFirst(Integer.valueOf(i));
        int first = ((Integer) d.peekFirst()).intValue();
        int last = ((Integer) d.peekLast()).intValue();
        return first * 1000 + last * 10 + d.size();
    }

    public static int offerInterleave() {
        ArrayDeque d = new ArrayDeque();
        for (int i = 1; i <= 6; i++) {
            if (i % 2 == 0) d.offerLast(Integer.valueOf(i));
            else d.offerFirst(Integer.valueOf(i));
        }
        int acc = d.size();
        Iterator it = d.iterator();
        while (it.hasNext()) {
            acc = acc * 31 + ((Integer) it.next()).intValue();
        }
        return acc;
    }

    public static int offerInterleaveDescending() {
        ArrayDeque d = new ArrayDeque();
        for (int i = 1; i <= 6; i++) {
            if (i % 2 == 0) d.offerLast(Integer.valueOf(i));
            else d.offerFirst(Integer.valueOf(i));
        }
        Iterator it = d.descendingIterator();
        int acc = 0;
        while (it.hasNext()) {
            acc = acc * 31 + ((Integer) it.next()).intValue();
        }
        return acc;
    }

    public static int removeLastOccurrenceAtHead() {
        ArrayDeque d = new ArrayDeque();
        int[] vals = {5, 1, 2, 3, 5};
        for (int v : vals) d.addLast(Integer.valueOf(v));
        // remove last 5 (tail), then last 5 (head)
        boolean r1 = d.removeLastOccurrence(Integer.valueOf(5));
        boolean r2 = d.removeLastOccurrence(Integer.valueOf(5));
        boolean r3 = d.removeLastOccurrence(Integer.valueOf(5));
        int acc = (r1 ? 1 : 0) * 100 + (r2 ? 1 : 0) * 10 + (r3 ? 1 : 0);
        Iterator it = d.iterator();
        while (it.hasNext()) {
            acc = acc * 31 + ((Integer) it.next()).intValue();
        }
        return acc;
    }
}
