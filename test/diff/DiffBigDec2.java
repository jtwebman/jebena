import java.math.BigDecimal;
import java.math.RoundingMode;

public class DiffBigDec2 {

    private static int cs(String s) {
        int acc = 0;
        for (int i = 0; i < s.length(); i++) {
            acc = acc * 31 + s.charAt(i);
        }
        return acc;
    }

    public static int div10by3Half() {
        BigDecimal r = new BigDecimal("10").divide(new BigDecimal("3"), 4, RoundingMode.HALF_UP);
        return cs(r.toPlainString());
    }

    public static int div1by8Down() {
        BigDecimal r = new BigDecimal("1").divide(new BigDecimal("8"), 5, RoundingMode.DOWN);
        return cs(r.toPlainString());
    }

    public static int divNeg10by3HalfEven() {
        BigDecimal r = new BigDecimal("-10").divide(new BigDecimal("3"), 2, RoundingMode.HALF_EVEN);
        return cs(r.toPlainString());
    }

    public static int div7by2Ceiling() {
        BigDecimal r = new BigDecimal("7").divide(new BigDecimal("2"), 0, RoundingMode.CEILING);
        return cs(r.toPlainString());
    }

    public static int div7by2Floor() {
        BigDecimal r = new BigDecimal("7").divide(new BigDecimal("2"), 0, RoundingMode.FLOOR);
        return cs(r.toPlainString());
    }

    public static int pow25cubed() {
        BigDecimal r = new BigDecimal("2.5").pow(3);
        return cs(r.toPlainString());
    }

    public static int pow2to10() {
        BigDecimal r = new BigDecimal("2").pow(10);
        return cs(r.toPlainString());
    }

    public static int strip() {
        BigDecimal r = new BigDecimal("1.2300").stripTrailingZeros();
        return cs(r.toPlainString());
    }

    public static int movePointLeft2() {
        BigDecimal r = new BigDecimal("123.45").movePointLeft(2);
        return cs(r.toPlainString());
    }

    public static int movePointRight1() {
        BigDecimal r = new BigDecimal("1.5").movePointRight(1);
        return cs(r.toPlainString());
    }
}
