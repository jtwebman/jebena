public class DiffPQ {

    public static int pollSorted() {
        java.util.PriorityQueue pq = new java.util.PriorityQueue();
        int[] vals = {5,1,4,2,8,3,7,6};
        for (int i = 0; i < vals.length; i++) pq.offer(Integer.valueOf(vals[i]));
        int acc = 0;
        while (!pq.isEmpty()) {
            Integer v = (Integer) pq.poll();
            acc = acc * 31 + v.intValue();
        }
        return acc;
    }

    public static int peekMin() {
        java.util.PriorityQueue pq = new java.util.PriorityQueue();
        pq.add(Integer.valueOf(9));
        pq.add(Integer.valueOf(3));
        pq.add(Integer.valueOf(7));
        pq.add(Integer.valueOf(1));
        pq.add(Integer.valueOf(5));
        Integer p1 = (Integer) pq.peek();
        Integer e1 = (Integer) pq.element();
        pq.poll();
        Integer p2 = (Integer) pq.peek();
        return p1.intValue() * 100 + e1.intValue() * 10 + p2.intValue();
    }

    public static int sizeTrack() {
        java.util.PriorityQueue pq = new java.util.PriorityQueue();
        int acc = 0;
        acc = acc * 31 + pq.size();
        pq.offer(Integer.valueOf(4));
        pq.offer(Integer.valueOf(2));
        pq.offer(Integer.valueOf(6));
        acc = acc * 31 + pq.size();
        pq.poll();
        acc = acc * 31 + pq.size();
        pq.clear();
        acc = acc * 31 + pq.size();
        acc = acc * 31 + (pq.isEmpty() ? 1 : 0);
        return acc;
    }

    public static int containsHitMiss() {
        java.util.PriorityQueue pq = new java.util.PriorityQueue();
        pq.offer(Integer.valueOf(10));
        pq.offer(Integer.valueOf(20));
        pq.offer(Integer.valueOf(30));
        int acc = 0;
        acc = acc * 31 + (pq.contains(Integer.valueOf(20)) ? 1 : 0);
        acc = acc * 31 + (pq.contains(Integer.valueOf(99)) ? 1 : 0);
        acc = acc * 31 + (pq.contains(Integer.valueOf(10)) ? 1 : 0);
        return acc;
    }

    public static int removeObj() {
        java.util.PriorityQueue pq = new java.util.PriorityQueue();
        int[] vals = {5,1,4,2,8,3,7,6};
        for (int i = 0; i < vals.length; i++) pq.offer(Integer.valueOf(vals[i]));
        boolean r1 = pq.remove(Integer.valueOf(4));
        boolean r2 = pq.remove(Integer.valueOf(99));
        int acc = (r1 ? 1 : 0) * 2 + (r2 ? 1 : 0);
        while (!pq.isEmpty()) {
            Integer v = (Integer) pq.poll();
            acc = acc * 31 + v.intValue();
        }
        return acc;
    }

    public static int removeHead() {
        java.util.PriorityQueue pq = new java.util.PriorityQueue();
        int[] vals = {5,1,4,2,8};
        for (int i = 0; i < vals.length; i++) pq.offer(Integer.valueOf(vals[i]));
        Integer h = (Integer) pq.remove();
        int acc = h.intValue();
        while (!pq.isEmpty()) {
            Integer v = (Integer) pq.poll();
            acc = acc * 31 + v.intValue();
        }
        return acc;
    }

    public static int emptyPoll() {
        java.util.PriorityQueue pq = new java.util.PriorityQueue();
        Object o = pq.poll();
        int acc = (o == null) ? -1 : 0;
        pq.offer(Integer.valueOf(42));
        Integer v = (Integer) pq.poll();
        acc = acc * 31 + v.intValue();
        Object o2 = pq.poll();
        acc = acc * 31 + ((o2 == null) ? -1 : 0);
        return acc;
    }

    public static int iterSum() {
        java.util.PriorityQueue pq = new java.util.PriorityQueue();
        int[] vals = {5,1,4,2,8,3};
        for (int i = 0; i < vals.length; i++) pq.offer(Integer.valueOf(vals[i]));
        int sum = 0;
        java.util.Iterator it = pq.iterator();
        while (it.hasNext()) {
            Integer v = (Integer) it.next();
            sum += v.intValue();
        }
        return sum;
    }
}
