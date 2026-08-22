/**
 * Differential coverage for String.format specifiers. Each case returns a rolling
 * hash of the formatted string (a = a*31 + ch, int-overflow identical on both
 * sides) so exact content/order/length is checked byte-for-byte vs real java.
 * Covers %d (width/zero/left/plus/comma-grouping), %x/%X/%o (+ '#' alt),
 * %s (width/precision/upper), %c, %b, %f, %%, and a mixed format.
 */
public class DiffFmt {
    private static int h(String s) {
        int a = 0;
        for (int i = 0; i < s.length(); i++) {
            a = a * 31 + s.charAt(i);
        }
        return a;
    }

    static int fmtD() {
        return h(String.format("%d", Integer.valueOf(-42)));
    }

    static int fmtWidth() {
        return h(String.format("[%5d]", Integer.valueOf(42)));
    }

    static int fmtZero() {
        return h(String.format("%05d", Integer.valueOf(42)));
    }

    static int fmtLeft() {
        return h(String.format("[%-5d]", Integer.valueOf(42)));
    }

    static int fmtPlus() {
        return h(String.format("%+d/%+d", Integer.valueOf(42), Integer.valueOf(-7)));
    }

    static int fmtComma() {
        return h(String.format("%,d", Integer.valueOf(1234567)));
    }

    static int fmtCommaNeg() {
        return h(String.format("%,d", Integer.valueOf(-1234567)));
    }

    static int fmtCommaLong() {
        return h(String.format("%,d", Long.valueOf(9876543210L)));
    }

    static int fmtHex() {
        return h(String.format("%x/%X", Integer.valueOf(255), Integer.valueOf(255)));
    }

    static int fmtHexAlt() {
        return h(String.format("%#x/%#X", Integer.valueOf(255), Integer.valueOf(255)));
    }

    static int fmtHexZero() {
        return h(String.format("%#06x", Integer.valueOf(255)));
    }

    static int fmtOct() {
        return h(String.format("%o/%#o", Integer.valueOf(8), Integer.valueOf(8)));
    }

    static int fmtStr() {
        return h(String.format("[%10s][%-10s]", "hi", "hi"));
    }

    static int fmtStrPrec() {
        return h(String.format("%.3s|%S", "hello", "hi"));
    }

    static int fmtChar() {
        return h(String.format("%c%c", Character.valueOf('A'), Integer.valueOf(66)));
    }

    static int fmtBool() {
        return h(String.format("%b/%b", Boolean.valueOf(true), null));
    }

    static int fmtPct() {
        return h(String.format("%d%% done", Integer.valueOf(50)));
    }

    static int fmtFloat() {
        return h(String.format("%.2f|%08.3f", Double.valueOf(3.14159), Double.valueOf(-2.5)));
    }

    static int fmtMix() {
        return h(String.format("%s=%05d (%,d) 0x%04x", "x", Integer.valueOf(42),
                Integer.valueOf(1000000), Integer.valueOf(4095)));
    }
}
