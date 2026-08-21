public class LongFields {
    long lv;
    double dv;
    int iv;

    LongFields(long l, double d, int i) {
        lv = l;
        dv = d;
        iv = i;
    }

    static int mixed() {
        LongFields a = new LongFields(1000000000000L, 3.5, 7);
        return (int) (a.lv % 1000000) + (int) (a.dv * 2) + a.iv;
    }

    static long longField() {
        LongFields a = new LongFields(9999999999L, 0, 0);
        a.lv = a.lv + 1;
        return a.lv;
    }

    static int arrayOfLongFields() {
        long sum = 0;
        for (int i = 0; i < 10; i++) {
            LongFields a = new LongFields((long) i * 1000000000L, i * 0.5, i);
            sum += a.lv;
            sum += (long) a.dv;
        }
        return (int) (sum % 100000);
    }
}
