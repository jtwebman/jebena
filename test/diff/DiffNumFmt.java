import java.text.NumberFormat;

public class DiffNumFmt {

    // Rolling checksum of a string: folds length and characters into one int.
    private static int ck(String s) {
        int acc = s.length();
        for (int i = 0; i < s.length(); i++) {
            acc = acc * 31 + s.charAt(i);
        }
        return acc;
    }

    public static int integerGrouped() {
        return ck(NumberFormat.getIntegerInstance().format(1234567));
    }

    public static int numberOneFrac() {
        return ck(NumberFormat.getInstance().format(1234.5));
    }

    public static int numberThreeFrac() {
        return ck(NumberFormat.getInstance().format(1234.567));
    }

    public static int numberLong() {
        return ck(NumberFormat.getNumberInstance().format(1000000L));
    }

    public static int maxFrac2() {
        NumberFormat nf = NumberFormat.getInstance();
        nf.setMaximumFractionDigits(2);
        return ck(nf.format(3.14159));
    }

    public static int groupingOff() {
        NumberFormat nf = NumberFormat.getInstance();
        nf.setGroupingUsed(false);
        return ck(nf.format(1234567L));
    }

    public static int minFracPad() {
        NumberFormat nf = NumberFormat.getInstance();
        nf.setMinimumFractionDigits(2);
        return ck(nf.format(5.0));
    }

    public static int negativeGrouped() {
        return ck(NumberFormat.getInstance().format(-1234.5));
    }

    public static int zeroValue() {
        return ck(NumberFormat.getInstance().format(0.0));
    }

    public static int integerHalfEven() {
        return ck(NumberFormat.getIntegerInstance().format(2.5));
    }
}
