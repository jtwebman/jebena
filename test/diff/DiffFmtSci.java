/**
 * Differential coverage for scientific/general floating-point conversions
 * (%e, %E, %g, %G) of java.util.Formatter. Each case returns a rolling hash
 * (a = a*31 + ch, int-overflow identical on both sides) of the formatted
 * string so exact content/length is checked byte-for-byte vs real java.
 */
public class DiffFmtSci {
    private static int h(String s) {
        int a = 0;
        for (int i = 0; i < s.length(); i++) {
            a = a * 31 + s.charAt(i);
        }
        return a;
    }

    public static int sciE() {
        return h(String.format("%e", Double.valueOf(12345.678)));
    }

    public static int sciEPrec2() {
        return h(String.format("%.2e", Double.valueOf(0.000123)));
    }

    public static int sciBigE() {
        return h(String.format("%E", Double.valueOf(6.022e23)));
    }

    public static int sciNeg() {
        return h(String.format("%e", Double.valueOf(-98765.4321)));
    }

    public static int sciPlus() {
        return h(String.format("%+.3e", Double.valueOf(42.0)));
    }

    public static int sciWidth() {
        return h(String.format("[%15.4e]", Double.valueOf(3.14159)));
    }

    public static int genG() {
        return h(String.format("%g", Double.valueOf(12345.678)));
    }

    public static int genSmall() {
        return h(String.format("%g", Double.valueOf(0.0001234)));
    }

    public static int genPrec3() {
        return h(String.format("%.3g", Double.valueOf(123456.0)));
    }

    public static int genUpper() {
        return h(String.format("%.4G", Double.valueOf(0.00007654321)));
    }
}
