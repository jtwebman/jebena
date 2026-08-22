package st;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Collections + nested-class stress. Each of 8 worker fibers independently
 * builds and drains a LinkedList and an ArrayDeque (forcing ring-buffer growth
 * past 16 and using the nested ArrayDeque$Itr iterator), plus walks a
 * self-referential in-app linked structure (nested static class Node). This
 * exercises: nested-class instantiation (LinkedList$Node, ArrayDeque$Itr, our
 * own Node) through the real class-load path, list mutation/indexing, and
 * concurrent allocation + GC over nested-class-heavy code at 1 & 4 carriers.
 * Deterministic: 8 * PER = 8 * 1660 = 13280.
 */
public class CollFuzz {
    static final class Node {
        int v;
        Node next;
        Node(int v, Node next) { this.v = v; this.next = next; }
    }

    // Per-worker deterministic computation.
    static int work() {
        int total = 0;

        // LinkedList: addLast 0..49, addFirst -1, index-sum, then trim ends.
        LinkedList l = new LinkedList();
        for (int i = 0; i < 50; i++) {
            l.addLast(Integer.valueOf(i));
        }
        l.addFirst(Integer.valueOf(-1));
        for (int i = 0; i < l.size(); i++) {
            total += ((Integer) l.get(i)).intValue(); // -1 + 0..49 = 1224
        }
        l.removeFirst();
        l.removeLast();
        total += l.size(); // 49 -> 1273

        // ArrayDeque: grow past 16, then sum via the nested Itr iterator.
        ArrayDeque d = new ArrayDeque();
        for (int i = 1; i <= 20; i++) {
            d.addLast(Integer.valueOf(i));
        }
        Iterator it = d.iterator();
        while (it.hasNext()) {
            total += ((Integer) it.next()).intValue(); // 1..20 = 210 -> 1483
        }

        // Self-referential nested Node chain 1..15, walked back to front.
        Node head = null;
        for (int i = 1; i <= 15; i++) {
            head = new Node(i, head);
        }
        for (Node n = head; n != null; n = n.next) {
            total += n.v; // 1..15 = 120 -> 1603
        }

        // A few boxed identity/equality touches to keep the allocator busy.
        for (int i = 0; i < 19; i++) {
            total += (Integer.valueOf(i).equals(Integer.valueOf(i))) ? 3 : 0; // 19*3 = 57 -> 1660
        }
        return total;
    }

    static final class Worker extends Thread {
        volatile int result;
        public void run() { result = work(); }
    }

    public static int demo() {
        Worker[] w = new Worker[8];
        for (int i = 0; i < w.length; i++) {
            w[i] = new Worker();
            w[i].start();
        }
        int sum = 0;
        for (int i = 0; i < w.length; i++) {
            try {
                w[i].join();
            } catch (InterruptedException e) {
                return -1;
            }
            sum += w[i].result;
        }
        return sum; // 8 * 1660 = 13280
    }
}
