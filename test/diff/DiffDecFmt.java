import java.text.DecimalFormat;

public class DiffDecFmt {

    // Rolling checksum of a string: folds length and characters into one int.
    private static int ck(String s) {
        int acc = s.length();
        for (int i = 0; i < s.length(); i++) {
            acc = acc * 31 + s.charAt(i);
        }
        return acc;
    }

    public static int grouped2dpHalfEven() {
        return ck(new DecimalFormat("#,##0.00").format(1234567.895));
    }

    public static int grouped2dpNegative() {
        return ck(new DecimalFormat("#,##0.00").format(-12.5));
    }

    public static int grouped2dpZero() {
        return ck(new DecimalFormat("#,##0.00").format(0.0));
    }

    public static int optionalFracRound() {
        return ck(new DecimalFormat("0.##").format(3.14159));
    }

    public static int optionalFracStrip() {
        return ck(new DecimalFormat("0.##").format(5.0));
    }

    public static int minIntPad() {
        return ck(new DecimalFormat("000").format(7L));
    }

    public static int groupedLong() {
        return ck(new DecimalFormat("#,##0").format(1234567L));
    }

    public static int oneFrac() {
        return ck(new DecimalFormat("0.0").format(2.0));
    }
}
