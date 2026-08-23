import java.math.BigDecimal;
import java.math.RoundingMode;

public class DiffBigDec {

    // Rolling checksum of a string: folds length and characters into one int.
    private static int ck(String s) {
        int acc = s.length();
        for (int i = 0; i < s.length(); i++) {
            acc = acc * 31 + s.charAt(i);
        }
        return acc;
    }

    public static int addPlain() {
        BigDecimal a = new BigDecimal("12.34");
        BigDecimal b = new BigDecimal("0.006");
        return ck(a.add(b).toPlainString());
    }

    public static int subtractPlain() {
        BigDecimal a = new BigDecimal("100.5");
        BigDecimal b = new BigDecimal("0.55");
        return ck(a.subtract(b).toPlainString());
    }

    public static int multiplyPlain() {
        BigDecimal a = new BigDecimal("2.5");
        BigDecimal b = new BigDecimal("1.03");
        return ck(a.multiply(b).toPlainString());
    }

    public static int negativeAddPlain() {
        BigDecimal a = new BigDecimal("-1.25");
        BigDecimal b = new BigDecimal("0.05");
        return ck(a.add(b).toPlainString());
    }

    public static int compareEqualValue() {
        BigDecimal a = new BigDecimal("1.50");
        BigDecimal b = new BigDecimal("1.5");
        return a.compareTo(b);
    }

    public static int compareGreater() {
        BigDecimal a = new BigDecimal("0.1");
        BigDecimal b = new BigDecimal("0.09");
        return a.compareTo(b);
    }

    public static int compareLess() {
        BigDecimal a = new BigDecimal("-2.0");
        BigDecimal b = new BigDecimal("0.001");
        return a.compareTo(b);
    }

    public static int signumNeg() {
        return new BigDecimal("-3.2").signum();
    }

    public static int scaleAfterMultiply() {
        BigDecimal a = new BigDecimal("1.20");
        BigDecimal b = new BigDecimal("3.400");
        return a.multiply(b).scale();
    }

    public static int setScaleHalfUp() {
        BigDecimal a = new BigDecimal("2.345");
        return ck(a.setScale(2, RoundingMode.HALF_UP).toPlainString());
    }

    public static int setScaleDown() {
        BigDecimal a = new BigDecimal("2.345");
        return ck(a.setScale(2, RoundingMode.DOWN).toPlainString());
    }

    public static int absPlain() {
        return ck(new BigDecimal("-5.5").abs().toPlainString());
    }

    public static int valueOfLong() {
        return ck(BigDecimal.valueOf(42L).toPlainString());
    }
}
