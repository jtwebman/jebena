public class DiffSB2 {
    private static int cs(String s) {
        int acc = 0;
        for (int i = 0; i < s.length(); i++) {
            acc = acc * 31 + s.charAt(i);
        }
        return acc;
    }

    public static int replaceMid() {
        StringBuilder sb = new StringBuilder("abcdef");
        sb.replace(1, 3, "XY");
        return cs(sb.toString());
    }

    public static int replaceGrow() {
        StringBuilder sb = new StringBuilder("abcdef");
        sb.replace(2, 3, "ZZZZ");
        return cs(sb.toString());
    }

    public static int replaceShrink() {
        StringBuilder sb = new StringBuilder("abcdef");
        sb.replace(1, 5, "Q");
        return cs(sb.toString());
    }

    public static int indexOfCd() {
        return new StringBuilder("abcdef").indexOf("cd");
    }

    public static int indexOfFrom() {
        return new StringBuilder("abcabc").indexOf("bc", 3);
    }

    public static int indexOfMiss() {
        return new StringBuilder("abcdef").indexOf("zz");
    }

    public static int lastIndexOfA() {
        return new StringBuilder("abcabc").lastIndexOf("a");
    }

    public static int lastIndexOfBc() {
        return new StringBuilder("abcabc").lastIndexOf("bc");
    }

    public static int insertInt() {
        StringBuilder sb = new StringBuilder("abcdef");
        sb.insert(2, 99);
        return cs(sb.toString());
    }

    public static int insertBool() {
        StringBuilder sb = new StringBuilder("abcdef");
        sb.insert(0, true);
        return cs(sb.toString());
    }

    public static int insertChar() {
        StringBuilder sb = new StringBuilder("abcdef");
        sb.insert(3, '#');
        return cs(sb.toString());
    }

    public static int insertLong() {
        StringBuilder sb = new StringBuilder("x");
        sb.insert(1, 123456789012L);
        return cs(sb.toString());
    }

    public static int insertChars() {
        StringBuilder sb = new StringBuilder("abcdef");
        sb.insert(2, new char[] {'1', '2', '3'});
        return cs(sb.toString());
    }

    public static int appendCp() {
        StringBuilder sb = new StringBuilder("z");
        sb.appendCodePoint(0x41);
        return cs(sb.toString());
    }

    public static int appendCpSupp() {
        StringBuilder sb = new StringBuilder();
        sb.appendCodePoint(0x1F600);
        return cs(sb.toString()) + sb.length();
    }

    public static int capacityCheck() {
        StringBuilder sb = new StringBuilder(20);
        sb.append("hi");
        return sb.capacity() + sb.length();
    }
}
