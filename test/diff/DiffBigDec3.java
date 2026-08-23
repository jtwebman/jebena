import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class DiffBigDec3 {

    private static int cksum(String s) {
        int acc = 0;
        for (int i = 0; i < s.length(); i++) {
            acc = acc * 31 + s.charAt(i);
        }
        return acc;
    }

    public static int remainderBasic() {
        BigDecimal a = new BigDecimal("10");
        BigDecimal b = new BigDecimal("3");
        return cksum(a.remainder(b).toPlainString());
    }

    public static int remainderFrac() {
        BigDecimal a = new BigDecimal("10.5");
        BigDecimal b = new BigDecimal("3");
        return cksum(a.remainder(b).toPlainString());
    }

    public static int remainderNeg() {
        BigDecimal a = new BigDecimal("-10");
        BigDecimal b = new BigDecimal("3");
        return cksum(a.remainder(b).toPlainString());
    }

    public static int divToIntegral() {
        BigDecimal a = new BigDecimal("10");
        BigDecimal b = new BigDecimal("3");
        return cksum(a.divideToIntegralValue(b).toPlainString());
    }

    public static int divToIntegralFrac() {
        BigDecimal a = new BigDecimal("10.5");
        BigDecimal b = new BigDecimal("0.5");
        return cksum(a.divideToIntegralValue(b).toPlainString());
    }

    public static int maxOf() {
        BigDecimal a = new BigDecimal("12.30");
        BigDecimal b = new BigDecimal("12.5");
        return cksum(a.max(b).toPlainString());
    }

    public static int minOf() {
        BigDecimal a = new BigDecimal("12.30");
        BigDecimal b = new BigDecimal("12.5");
        return cksum(a.min(b).toPlainString());
    }

    public static int precisionOf() {
        return new BigDecimal("123.45").precision();
    }

    public static int precisionZero() {
        return new BigDecimal("0.000").precision();
    }

    public static int roundBasic() {
        BigDecimal a = new BigDecimal("123.456");
        return cksum(a.round(new MathContext(4)).toPlainString());
    }

    public static int roundCarry() {
        BigDecimal a = new BigDecimal("99.9");
        return cksum(a.round(new MathContext(2)).toPlainString());
    }

    public static int roundFloorMode() {
        BigDecimal a = new BigDecimal("123.456");
        return cksum(a.round(new MathContext(4, RoundingMode.FLOOR)).toPlainString());
    }
}
