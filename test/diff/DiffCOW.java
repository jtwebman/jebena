import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Iterator;

public class DiffCOW {
    public static int addGetFold() {
        CopyOnWriteArrayList l = new CopyOnWriteArrayList();
        for (int i = 1; i <= 5; i++) {
            l.add(Integer.valueOf(i));
        }
        int sum = 0;
        for (int i = 0; i < l.size(); i++) {
            sum += ((Integer) l.get(i)).intValue();
        }
        return sum + l.size();
    }

    public static int setThenGet() {
        CopyOnWriteArrayList l = new CopyOnWriteArrayList();
        for (int i = 1; i <= 5; i++) {
            l.add(Integer.valueOf(i));
        }
        Object old = l.set(2, Integer.valueOf(99));
        int oldv = ((Integer) old).intValue();
        int now = ((Integer) l.get(2)).intValue();
        return oldv * 100 + now;
    }

    public static int removeIntSizeChecksum() {
        CopyOnWriteArrayList l = new CopyOnWriteArrayList();
        for (int i = 1; i <= 5; i++) {
            l.add(Integer.valueOf(i));
        }
        Object removed = l.remove(1);
        int checksum = 0;
        for (int i = 0; i < l.size(); i++) {
            checksum = checksum * 10 + ((Integer) l.get(i)).intValue();
        }
        return ((Integer) removed).intValue() * 100000 + l.size() * 10000 + checksum;
    }

    public static int addIfAbsentExisting() {
        CopyOnWriteArrayList l = new CopyOnWriteArrayList();
        for (int i = 1; i <= 5; i++) {
            l.add(Integer.valueOf(i));
        }
        boolean r = l.addIfAbsent(Integer.valueOf(3));
        return (r ? 1000 : 0) + l.size();
    }

    public static int addIfAbsentNew() {
        CopyOnWriteArrayList l = new CopyOnWriteArrayList();
        for (int i = 1; i <= 5; i++) {
            l.add(Integer.valueOf(i));
        }
        boolean r = l.addIfAbsent(Integer.valueOf(42));
        int last = ((Integer) l.get(l.size() - 1)).intValue();
        return (r ? 1000 : 0) + l.size() * 100 + last;
    }

    public static int indexOfHitMiss() {
        CopyOnWriteArrayList l = new CopyOnWriteArrayList();
        for (int i = 1; i <= 5; i++) {
            l.add(Integer.valueOf(i * 2));
        }
        int hit = l.indexOf(Integer.valueOf(6));
        int miss = l.indexOf(Integer.valueOf(7));
        return hit * 100 + (miss + 1);
    }

    public static int containsCheck() {
        CopyOnWriteArrayList l = new CopyOnWriteArrayList();
        for (int i = 1; i <= 5; i++) {
            l.add(Integer.valueOf(i));
        }
        int r = 0;
        if (l.contains(Integer.valueOf(4))) {
            r += 10;
        }
        if (!l.contains(Integer.valueOf(99))) {
            r += 1;
        }
        return r;
    }

    public static int iteratorSnapshotSum() {
        CopyOnWriteArrayList l = new CopyOnWriteArrayList();
        for (int i = 1; i <= 5; i++) {
            l.add(Integer.valueOf(i));
        }
        Iterator it = l.iterator();
        l.add(Integer.valueOf(100));
        l.remove(0);
        int sum = 0;
        while (it.hasNext()) {
            sum += ((Integer) it.next()).intValue();
        }
        return sum;
    }

    public static int addAtIndex() {
        CopyOnWriteArrayList l = new CopyOnWriteArrayList();
        for (int i = 1; i <= 5; i++) {
            l.add(Integer.valueOf(i));
        }
        l.add(2, Integer.valueOf(77));
        int checksum = 0;
        for (int i = 0; i < l.size(); i++) {
            checksum = checksum * 10 + (((Integer) l.get(i)).intValue() % 10);
        }
        return checksum + l.size() * 1000000;
    }

    public static int removeObject() {
        CopyOnWriteArrayList l = new CopyOnWriteArrayList();
        for (int i = 1; i <= 5; i++) {
            l.add(Integer.valueOf(i));
        }
        boolean r1 = l.remove(Integer.valueOf(3));
        boolean r2 = l.remove(Integer.valueOf(99));
        return (r1 ? 100 : 0) + (r2 ? 10 : 0) + l.size();
    }
}
