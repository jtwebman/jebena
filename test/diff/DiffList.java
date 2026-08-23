import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Differential coverage for list/deque/Collections/Arrays breadth: LinkedList as a
 * Deque and as a List, Iterator.remove during iteration, Collections helpers
 * (reverse/max/min/singletonList/nCopies/frequency/swap), and Arrays fill/copyOf/
 * copyOfRange/binarySearch. Each case returns a deterministic int vs real java.
 */
public class DiffList {
    static int dequeOps() {
        LinkedList d = new LinkedList();
        d.addFirst(Integer.valueOf(2));
        d.addFirst(Integer.valueOf(1));
        d.addLast(Integer.valueOf(3));
        d.push(Integer.valueOf(0)); // -> [0,1,2,3]
        int acc = 0;
        acc = acc * 10 + ((Integer) d.peekFirst()).intValue(); // 0
        acc = acc * 10 + ((Integer) d.peekLast()).intValue(); // 3
        acc = acc * 10 + ((Integer) d.pop()).intValue(); // removes 0
        acc = acc * 10 + ((Integer) d.removeLast()).intValue(); // removes 3
        acc = acc * 10 + d.size(); // 2 left [1,2]
        return acc; // 0 3 0 3 2 -> 3032... let's compute: 0,3,0,3,2 => 3032? acc: 0;0*10+3=3;3*10+0=30;30*10+3=303;303*10+2=3032
    }

    static int llAsList() {
        LinkedList l = new LinkedList();
        for (int i = 10; i <= 50; i += 10) {
            l.add(Integer.valueOf(i));
        }
        l.set(2, Integer.valueOf(99)); // [10,20,99,40,50]
        return l.indexOf(Integer.valueOf(99)) * 1000
                + ((Integer) l.get(4)).intValue() * 10
                + l.size(); // 2*1000 + 50*10 + 5 = 2505
    }

    static int iterRemove() {
        ArrayList a = new ArrayList();
        for (int i = 1; i <= 10; i++) {
            a.add(Integer.valueOf(i));
        }
        Iterator it = a.iterator();
        while (it.hasNext()) {
            int v = ((Integer) it.next()).intValue();
            if (v % 3 == 0) {
                it.remove(); // remove 3,6,9
            }
        }
        int sum = 0;
        for (int i = 0; i < a.size(); i++) {
            sum += ((Integer) a.get(i)).intValue();
        }
        return a.size() * 1000 + sum; // 7 left, sum 55-18=37 -> 7037
    }

    static int collReverseMaxMin() {
        ArrayList a = new ArrayList();
        int[] v = { 5, 2, 8, 1, 9, 3 };
        for (int x : v) {
            a.add(Integer.valueOf(x));
        }
        int max = ((Integer) Collections.max(a)).intValue(); // 9
        int min = ((Integer) Collections.min(a)).intValue(); // 1
        Collections.reverse(a); // [3,9,1,8,2,5]
        return max * 1000 + min * 100 + ((Integer) a.get(0)).intValue() * 10 + ((Integer) a.get(5)).intValue();
        // 9000 + 100 + 30 + 5 = 9135
    }

    static int collHelpers() {
        List s = Collections.singletonList(Integer.valueOf(7));
        List n = Collections.nCopies(4, Integer.valueOf(2));
        int freq = Collections.frequency(n, Integer.valueOf(2)); // 4
        ArrayList sw = new ArrayList();
        sw.add(Integer.valueOf(1));
        sw.add(Integer.valueOf(2));
        sw.add(Integer.valueOf(3));
        Collections.swap(sw, 0, 2); // [3,2,1]
        return s.size() * 10000 + ((Integer) s.get(0)).intValue() * 1000
                + n.size() * 100 + freq * 10 + ((Integer) sw.get(0)).intValue();
        // 1*10000 + 7*1000 + 4*100 + 4*10 + 3 = 10000+7000+400+40+3 = 17443
    }

    static int arraysOps() {
        int[] a = new int[5];
        Arrays.fill(a, 7); // [7,7,7,7,7]
        int[] b = Arrays.copyOf(a, 3); // [7,7,7]
        int[] c = Arrays.copyOfRange(a, 1, 4); // [7,7,7]
        int[] d = { 1, 3, 5, 7, 9 };
        int idx = Arrays.binarySearch(d, 7); // 3
        int sum = 0;
        for (int x : b) {
            sum += x;
        }
        for (int x : c) {
            sum += x;
        }
        return sum * 100 + idx * 10 + a.length; // (21+21)*100 + 30 + 5 = 4235
    }

    static int arraysEqToStr() {
        int[] a = { 1, 2, 3 };
        int[] b = { 1, 2, 3 };
        int[] c = { 1, 2, 4 };
        int eq = (Arrays.equals(a, b) ? 1 : 0) * 10 + (Arrays.equals(a, c) ? 1 : 0); // 10
        String s = Arrays.toString(a); // "[1, 2, 3]"
        return eq * 100 + s.length(); // 10*100 + 9 = 1009
    }
}
