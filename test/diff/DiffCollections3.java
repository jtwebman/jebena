import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Comparator;
import java.util.Random;

public class DiffCollections3 {

    static int checksum(List list) {
        int h = 0;
        for (int i = 0; i < list.size(); i++) {
            h = h * 31 + ((Integer) list.get(i)).intValue() * (i + 1);
        }
        return h;
    }

    static List nums(int n) {
        ArrayList l = new ArrayList();
        for (int i = 1; i <= n; i++) {
            l.add(Integer.valueOf(i));
        }
        return l;
    }

    public static int rotateChecksum() {
        List l = nums(5);
        Collections.rotate(l, 2);
        return checksum(l);
    }

    public static int rotateNegative() {
        List l = nums(5);
        Collections.rotate(l, -1);
        return checksum(l);
    }

    public static int replaceAllChecksum() {
        ArrayList l = new ArrayList();
        l.add(Integer.valueOf(1));
        l.add(Integer.valueOf(2));
        l.add(Integer.valueOf(2));
        l.add(Integer.valueOf(3));
        boolean r = Collections.replaceAll(l, Integer.valueOf(2), Integer.valueOf(9));
        return (r ? 1000000 : 0) + checksum(l);
    }

    public static int replaceAllNoMatch() {
        ArrayList l = new ArrayList();
        l.add(Integer.valueOf(1));
        l.add(Integer.valueOf(2));
        boolean r = Collections.replaceAll(l, Integer.valueOf(7), Integer.valueOf(9));
        return (r ? 1000000 : 0) + checksum(l);
    }

    public static int copyChecksum() {
        List dest = nums(5);
        ArrayList src = new ArrayList();
        src.add(Integer.valueOf(10));
        src.add(Integer.valueOf(20));
        src.add(Integer.valueOf(30));
        Collections.copy(dest, src);
        return checksum(dest);
    }

    public static int binarySearchNatural() {
        List l = nums(10);
        int a = Collections.binarySearch(l, Integer.valueOf(7));
        int b = Collections.binarySearch(l, Integer.valueOf(1));
        int c = Collections.binarySearch(l, Integer.valueOf(10));
        return a * 100 + b * 10 + c;
    }

    public static int binarySearchMissing() {
        ArrayList l = new ArrayList();
        l.add(Integer.valueOf(2));
        l.add(Integer.valueOf(4));
        l.add(Integer.valueOf(6));
        return Collections.binarySearch(l, Integer.valueOf(5));
    }

    public static int shuffleChecksum() {
        List l = nums(10);
        Collections.shuffle(l, new Random(42));
        return checksum(l);
    }

    public static int reverseOrderSort() {
        List l = nums(6);
        Collections.sort(l, Collections.reverseOrder());
        return checksum(l);
    }
}
