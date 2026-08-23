public class DiffFmtGrp {

    // Deterministic int from a formatted string: length in the high part,
    // a bounded, always-positive hash in the low part.
    private static int h(String s) {
        return s.length() * 1000000 + (s.hashCode() & 0xFFFFF);
    }

    private static int countChar(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) n++;
        }
        return n;
    }

    public static int commaInt() {
        return h(String.format("%,d", Integer.valueOf(1234567)));
    }

    public static int commaIntCommaCount() {
        return countChar(String.format("%,d", Integer.valueOf(1000000)), ',');
    }

    public static int plusPositive() {
        return h(String.format("%+d", Integer.valueOf(42)));
    }

    public static int plusNegative() {
        return h(String.format("%+d", Integer.valueOf(-42)));
    }

    public static int parenNegative() {
        return h(String.format("%(d", Integer.valueOf(-1234)));
    }

    public static int parenPositive() {
        return h(String.format("%(d", Integer.valueOf(1234)));
    }

    public static int parenCommaIntNeg() {
        return h(String.format("%(,d", Integer.valueOf(-1234567)));
    }

    public static int spaceForPlus() {
        return h(String.format("% d", Integer.valueOf(42)));
    }

    public static int spaceNegative() {
        return h(String.format("% d", Integer.valueOf(-42)));
    }

    public static int commaFloat() {
        return h(String.format("%,.2f", Double.valueOf(1234567.891)));
    }

    public static int parenCommaFloatNeg() {
        return h(String.format("%(,.2f", Double.valueOf(-1234.5)));
    }

    public static int commaLong() {
        return h(String.format("%,d", Long.valueOf(1234567890123L)));
    }
}
