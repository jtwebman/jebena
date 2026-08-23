import java.util.HashSet;

/**
 * Differential coverage for java.lang.Math additions (exp/log/log10/hypot/round +
 * toIntExact/addExact/subtractExact/multiplyExact/floorDiv(long)/floorMod(long)) and
 * java.util.HashSet set-operations (addAll/retainAll/removeAll/containsAll). Doubles
 * are compared via Double.doubleToLongBits so bit-exact results are checked as ints.
 */
public class DiffMath {
    private static int dbits(double d) {
        long b = Double.doubleToLongBits(d);
        return (int) (b ^ (b >>> 32));
    }


    static int limits() {
        // Integer/Long MIN_VALUE/MAX_VALUE + wraparound semantics
        int a = Integer.MAX_VALUE + 1 == Integer.MIN_VALUE ? 1 : 0; // 1 (int wrap)
        int b = Integer.MAX_VALUE - Integer.MIN_VALUE; // -1
        long c = Long.MAX_VALUE + 1L == Long.MIN_VALUE ? 1 : 0; // 1
        return a * 1000 + (b + 10) * 10 + (int) c + Integer.SIZE + Long.SIZE / 8;
        // 1*1000 + 9*10 + 1 + 32 + 8 = 1131
    }

    static int hypot() {
        return dbits(Math.hypot(3.0, 4.0)) ^ dbits(Math.hypot(5.0, 12.0)); // 5.0, 13.0
    }

    static int round() {
        long a = Math.round(2.5); // 3
        long b = Math.round(-2.5); // -2 (round half up toward +inf via floor(x+0.5))
        int c = Math.round(2.4f); // 2
        int d = Math.round(3.5f); // 4
        return (int) (a * 1000 + (b + 10) * 100) + c * 10 + d; // 3*1000 + 8*100 + 20 + 4 = 3824
    }

    static int exactOk() {
        return Math.toIntExact(123456789012L / 1000000L) // 123456
                + Math.addExact(1000, 2000) // 3000
                + Math.subtractExact(10, 3) // 7
                + Math.multiplyExact(6, 7); // 42
        // 123456 + 3000 + 7 + 42 = 126505
    }

    static int addOverflow() {
        try {
            Math.addExact(Integer.MAX_VALUE, 1);
            return -1;
        } catch (ArithmeticException e) {
            return 1;
        }
    }

    static int mulOverflow() {
        try {
            Math.multiplyExact(100000, 100000); // 10^10 > int
            return -1;
        } catch (ArithmeticException e) {
            return 1;
        }
    }

    static int floorLong() {
        return (int) Math.floorDiv(-7L, 2L) * 100 + (int) Math.floorMod(-7L, 2L) + 10;
        // floorDiv(-7,2) = -4; floorMod(-7,2) = 1 -> -4*100 + 1 + 10 = -389
    }

    static int setAddAll() {
        HashSet a = new HashSet();
        a.add(Integer.valueOf(1));
        a.add(Integer.valueOf(2));
        HashSet b = new HashSet();
        b.add(Integer.valueOf(2));
        b.add(Integer.valueOf(3));
        b.add(Integer.valueOf(4));
        boolean changed = a.addAll(b); // a = {1,2,3,4}
        return a.size() * 10 + (changed ? 1 : 0); // 41
    }

    static int setRetainRemove() {
        HashSet a = new HashSet();
        for (int i = 1; i <= 6; i++) {
            a.add(Integer.valueOf(i));
        }
        HashSet keep = new HashSet();
        keep.add(Integer.valueOf(2));
        keep.add(Integer.valueOf(4));
        keep.add(Integer.valueOf(6));
        keep.add(Integer.valueOf(99));
        a.retainAll(keep); // a = {2,4,6}
        int afterRetain = a.size(); // 3
        HashSet rm = new HashSet();
        rm.add(Integer.valueOf(4));
        a.removeAll(rm); // a = {2,6}
        return afterRetain * 100 + a.size() * 10 + (a.contains(Integer.valueOf(2)) ? 1 : 0); // 321
    }

    static int setContainsAll() {
        HashSet a = new HashSet();
        for (int i = 1; i <= 5; i++) {
            a.add(Integer.valueOf(i));
        }
        HashSet sub = new HashSet();
        sub.add(Integer.valueOf(2));
        sub.add(Integer.valueOf(4));
        HashSet notsub = new HashSet();
        notsub.add(Integer.valueOf(2));
        notsub.add(Integer.valueOf(9));
        return (a.containsAll(sub) ? 1 : 0) * 10 + (a.containsAll(notsub) ? 1 : 0); // 10
    }
}
