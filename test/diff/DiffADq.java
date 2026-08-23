public class DiffADq {
    public static int fifo() {
        java.util.ArrayDeque q = new java.util.ArrayDeque();
        for (int i = 1; i <= 6; i++) q.offer(Integer.valueOf(i));
        int acc = 0;
        while (!q.isEmpty()) {
            Integer v = (Integer) q.poll();
            acc = acc * 31 + v.intValue();
        }
        return acc;
    }

    public static int lifo() {
        java.util.ArrayDeque q = new java.util.ArrayDeque();
        for (int i = 1; i <= 6; i++) q.push(Integer.valueOf(i));
        int acc = 0;
        while (!q.isEmpty()) {
            Integer v = (Integer) q.pop();
            acc = acc * 31 + v.intValue();
        }
        return acc;
    }

    public static int bothEnds() {
        java.util.ArrayDeque q = new java.util.ArrayDeque();
        q.addLast(Integer.valueOf(1));
        q.addFirst(Integer.valueOf(2));
        q.addLast(Integer.valueOf(3));
        q.addFirst(Integer.valueOf(4));
        Integer pf = (Integer) q.peekFirst();
        Integer pl = (Integer) q.peekLast();
        return pf.intValue() * 100 + pl.intValue() * 10 + q.size();
    }

    public static int pollEnds() {
        java.util.ArrayDeque q = new java.util.ArrayDeque();
        for (int i = 1; i <= 6; i++) q.offerLast(Integer.valueOf(i));
        int acc = 0;
        boolean fromFront = true;
        while (!q.isEmpty()) {
            Integer v = fromFront ? (Integer) q.pollFirst() : (Integer) q.pollLast();
            acc = acc * 31 + v.intValue();
            fromFront = !fromFront;
        }
        return acc;
    }

    public static int emptyPoll() {
        java.util.ArrayDeque q = new java.util.ArrayDeque();
        int acc = 0;
        Object a = q.poll();
        acc = acc * 31 + (a == null ? -1 : 0);
        Object b = q.pollFirst();
        acc = acc * 31 + (b == null ? -1 : 0);
        Object c = q.pollLast();
        acc = acc * 31 + (c == null ? -1 : 0);
        Object d = q.peek();
        acc = acc * 31 + (d == null ? -1 : 0);
        Object e = q.peekFirst();
        acc = acc * 31 + (e == null ? -1 : 0);
        Object f = q.peekLast();
        acc = acc * 31 + (f == null ? -1 : 0);
        return acc;
    }

    public static int sizeTrack() {
        java.util.ArrayDeque q = new java.util.ArrayDeque();
        int acc = 0;
        acc = acc * 31 + q.size();
        q.offerFirst(Integer.valueOf(10));
        acc = acc * 31 + q.size();
        q.offerLast(Integer.valueOf(20));
        q.offerLast(Integer.valueOf(30));
        acc = acc * 31 + q.size();
        q.pollFirst();
        acc = acc * 31 + q.size();
        acc = acc * 31 + (q.isEmpty() ? 1 : 0);
        q.pollLast();
        q.pollLast();
        acc = acc * 31 + q.size();
        acc = acc * 31 + (q.isEmpty() ? 1 : 0);
        return acc;
    }

    public static int contains() {
        java.util.ArrayDeque q = new java.util.ArrayDeque();
        q.addLast(Integer.valueOf(5));
        q.addLast(Integer.valueOf(7));
        q.addFirst(Integer.valueOf(3));
        int acc = 0;
        acc = acc * 31 + (q.contains(Integer.valueOf(5)) ? 1 : 0);
        acc = acc * 31 + (q.contains(Integer.valueOf(7)) ? 1 : 0);
        acc = acc * 31 + (q.contains(Integer.valueOf(3)) ? 1 : 0);
        acc = acc * 31 + (q.contains(Integer.valueOf(99)) ? 1 : 0);
        return acc;
    }

    public static int offerPeek() {
        java.util.ArrayDeque q = new java.util.ArrayDeque();
        int acc = 0;
        acc = acc * 31 + (q.offerFirst(Integer.valueOf(1)) ? 1 : 0);
        acc = acc * 31 + (q.offerLast(Integer.valueOf(2)) ? 1 : 0);
        Integer pk = (Integer) q.peek();
        acc = acc * 31 + pk.intValue();
        Integer pf = (Integer) q.peekFirst();
        acc = acc * 31 + pf.intValue();
        Integer pl = (Integer) q.peekLast();
        acc = acc * 31 + pl.intValue();
        return acc;
    }
}
