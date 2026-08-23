public class DiffStr3 {
    public static int stripLeadingLen() {
        String s = "\t\n\r  abc";
        return s.stripLeading().length();
    }

    public static int stripTrailingLen() {
        String s = "abc  \t\r\n";
        return s.stripTrailing().length();
    }

    public static int stripBothLen() {
        String s = "\t hello world \r\n";
        return s.strip().length();
    }

    public static int charsSum() {
        return "ABC".chars().sum();
    }

    public static int charsSumLong() {
        int acc = 0;
        String s = "The quick brown fox";
        int[] cp = s.chars().toArray();
        for (int i = 0; i < cp.length; i++) {
            acc = acc * 31 + cp[i];
        }
        return acc;
    }

    public static int charsCount() {
        return (int) "hello".chars().count();
    }

    public static int linesCount() {
        return (int) "a\nb\r\nc\nd".lines().count();
    }

    public static int linesCountTrailing() {
        return (int) "x\ny\n".lines().count();
    }

    public static int linesChecksum() {
        String s = "one\ntwo\r\nthree\nfour";
        Object[] arr = "one\ntwo\r\nthree\nfour".lines().toArray();
        int acc = 0;
        for (int i = 0; i < arr.length; i++) {
            acc = acc * 31 + ((String) arr[i]).length();
        }
        return acc;
    }

    public static int formattedLen() {
        return "%d-%s".formatted(Integer.valueOf(42), "hi").length();
    }

    public static int formattedHash() {
        return "%d-%s".formatted(Integer.valueOf(42), "hi").hashCode();
    }

    public static int isBlankFlags() {
        int a = "   \t\r\n".isBlank() ? 1 : 0;
        int b = " a ".isBlank() ? 1 : 0;
        int c = "".isBlank() ? 1 : 0;
        return a * 100 + b * 10 + c;
    }
}
