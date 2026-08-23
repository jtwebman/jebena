import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

public class DiffColl5 {

    public static int unmodListThrows() {
        List base = new ArrayList();
        base.add(Integer.valueOf(1));
        base.add(Integer.valueOf(2));
        List ul = Collections.unmodifiableList(base);
        int acc = ul.size() * 10;
        try {
            ul.set(0, Integer.valueOf(9));
            acc += 0;
        } catch (UnsupportedOperationException e) {
            acc += 1;
        }
        try {
            ul.add(Integer.valueOf(3));
            acc += 0;
        } catch (UnsupportedOperationException e) {
            acc += 10;
        }
        try {
            ul.remove(0);
            acc += 0;
        } catch (UnsupportedOperationException e) {
            acc += 100;
        }
        // read still works
        acc = acc * 100 + ((Integer) ul.get(1)).intValue();
        return acc;
    }

    public static int unmodSetThrows() {
        Set base = new HashSet();
        base.add(Integer.valueOf(5));
        base.add(Integer.valueOf(7));
        Set us = Collections.unmodifiableSet(base);
        int acc = us.size();
        try {
            us.add(Integer.valueOf(9));
            acc = acc * 10;
        } catch (UnsupportedOperationException e) {
            acc = acc * 10 + 1;
        }
        try {
            us.remove(Integer.valueOf(5));
            acc = acc * 10;
        } catch (UnsupportedOperationException e) {
            acc = acc * 10 + 1;
        }
        acc = acc * 10 + (us.contains(Integer.valueOf(7)) ? 1 : 0);
        return acc;
    }

    public static int unmodMapThrows() {
        Map base = new HashMap();
        base.put("a", Integer.valueOf(1));
        base.put("b", Integer.valueOf(2));
        Map um = Collections.unmodifiableMap(base);
        int acc = um.size();
        try {
            um.put("c", Integer.valueOf(3));
            acc = acc * 10;
        } catch (UnsupportedOperationException e) {
            acc = acc * 10 + 1;
        }
        try {
            um.remove("a");
            acc = acc * 10;
        } catch (UnsupportedOperationException e) {
            acc = acc * 10 + 1;
        }
        try {
            um.clear();
            acc = acc * 10;
        } catch (UnsupportedOperationException e) {
            acc = acc * 10 + 1;
        }
        acc = acc * 100 + ((Integer) um.get("b")).intValue();
        return acc;
    }

    public static int frequencyCount() {
        List l = new ArrayList();
        int[] vals = {1, 2, 2, 3, 2, 3, 1, 2};
        for (int i = 0; i < vals.length; i++) l.add(Integer.valueOf(vals[i]));
        int f1 = Collections.frequency(l, Integer.valueOf(1));
        int f2 = Collections.frequency(l, Integer.valueOf(2));
        int f3 = Collections.frequency(l, Integer.valueOf(3));
        int f9 = Collections.frequency(l, Integer.valueOf(9));
        return f1 * 1000 + f2 * 100 + f3 * 10 + f9;
    }

    public static int disjointTrue() {
        List a = new ArrayList();
        a.add(Integer.valueOf(1));
        a.add(Integer.valueOf(2));
        List b = new ArrayList();
        b.add(Integer.valueOf(3));
        b.add(Integer.valueOf(4));
        return Collections.disjoint(a, b) ? 1 : 0;
    }

    public static int disjointFalse() {
        List a = new ArrayList();
        a.add(Integer.valueOf(1));
        a.add(Integer.valueOf(2));
        List b = new ArrayList();
        b.add(Integer.valueOf(2));
        b.add(Integer.valueOf(4));
        return Collections.disjoint(a, b) ? 1 : 0;
    }

    public static int nCopiesSum() {
        List l = Collections.nCopies(5, Integer.valueOf(7));
        int sum = 0;
        for (int i = 0; i < l.size(); i++) {
            sum += ((Integer) l.get(i)).intValue();
        }
        return sum * 100 + l.size();
    }

    public static int swapValues() {
        List l = new ArrayList();
        for (int i = 0; i < 5; i++) l.add(Integer.valueOf(i));
        Collections.swap(l, 0, 4);
        Collections.swap(l, 1, 3);
        int acc = 0;
        for (int i = 0; i < l.size(); i++) {
            acc = acc * 10 + ((Integer) l.get(i)).intValue();
        }
        return acc;
    }

    public static int rotateChecksum() {
        List l = new ArrayList();
        for (int i = 1; i <= 5; i++) l.add(Integer.valueOf(i));
        Collections.rotate(l, 2);
        int acc = 0;
        for (int i = 0; i < l.size(); i++) {
            acc = acc * 10 + ((Integer) l.get(i)).intValue();
        }
        return acc;
    }

    public static int rotateNegative() {
        List l = new ArrayList();
        for (int i = 1; i <= 5; i++) l.add(Integer.valueOf(i));
        Collections.rotate(l, -1);
        int acc = 0;
        for (int i = 0; i < l.size(); i++) {
            acc = acc * 10 + ((Integer) l.get(i)).intValue();
        }
        return acc;
    }

    public static int fillList() {
        List l = new ArrayList();
        for (int i = 0; i < 4; i++) l.add(Integer.valueOf(i));
        Collections.fill(l, Integer.valueOf(8));
        int sum = 0;
        for (int i = 0; i < l.size(); i++) {
            sum += ((Integer) l.get(i)).intValue();
        }
        return sum * 10 + l.size();
    }
}
