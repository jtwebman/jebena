public class DiffChar2 {
    public static int charCountA() {
        return Character.charCount('A');
    }

    public static int charCountEmoji() {
        return Character.charCount(0x1F600);
    }

    public static int toCharsLen() {
        return Character.toChars(0x1F600).length;
    }

    public static int toCharsChecksum() {
        char[] r = Character.toChars(0x1F600);
        int acc = 0;
        for (int i = 0; i < r.length; i++) {
            acc = acc * 31 + r[i];
        }
        return acc;
    }

    public static int codePointAtA() {
        String s = "A😀";
        return Character.codePointAt(s, 0);
    }

    public static int codePointAtEmoji() {
        String s = "A😀";
        return Character.codePointAt(s, 1);
    }

    public static int isSurrogateHigh() {
        return Character.isSurrogate('\uD83D') ? 1 : 0;
    }

    public static int isHighSurrogateTest() {
        return Character.isHighSurrogate('\uD83D') ? 1 : 0;
    }

    public static int isLowSurrogateTest() {
        return Character.isLowSurrogate('\uDE00') ? 1 : 0;
    }

    public static int isAlphabeticZ() {
        return Character.isAlphabetic('z') ? 1 : 0;
    }

    public static int isAlphabetic5() {
        return Character.isAlphabetic('5') ? 1 : 0;
    }

    public static int toCodePointTest() {
        return Character.toCodePoint('\uD83D', '\uDE00');
    }
}
