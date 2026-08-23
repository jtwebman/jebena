import java.util.Scanner;

public class DiffScanner2 {

    private static int cs(String s) {
        int h = 0;
        for (int i = 0; i < s.length(); i++) {
            h = h * 31 + s.charAt(i);
        }
        return h;
    }

    // nextBoolean twice on "true false x", then hasNextBoolean on "x".
    public static int boolTwice() {
        Scanner sc = new Scanner("true false x");
        boolean a = sc.nextBoolean();
        boolean b = sc.nextBoolean();
        boolean has = sc.hasNextBoolean();
        return (a ? 1 : 0) * 100 + (b ? 1 : 0) * 10 + (has ? 1 : 0);
    }

    // case-insensitive booleans, mixed with a non-boolean tail.
    public static int boolMixed() {
        Scanner sc = new Scanner("TRUE False nope");
        boolean has0 = sc.hasNextBoolean();
        boolean a = sc.nextBoolean();
        boolean b = sc.nextBoolean();
        boolean has1 = sc.hasNextBoolean();
        return (has0 ? 1 : 0) * 1000 + (a ? 1 : 0) * 100
                + (b ? 1 : 0) * 10 + (has1 ? 1 : 0);
    }

    // useDelimiter(",") splitting "a,b,c"; fold the three tokens.
    public static int delimComma() {
        Scanner sc = new Scanner("a,b,c");
        sc.useDelimiter(",");
        String out = sc.next() + sc.next() + sc.next();
        return cs(out);
    }

    // useDelimiter(";") with integer tokens.
    public static int delimSemicolonInts() {
        Scanner sc = new Scanner("10;20;30");
        sc.useDelimiter(";");
        return sc.nextInt() + sc.nextInt() + sc.nextInt();
    }

    // hasNext(pattern): first token matches digits, not lowercase letters.
    public static int hasNextDigits() {
        Scanner sc = new Scanner("42 x");
        boolean d = sc.hasNext("[0-9]+");
        boolean l = sc.hasNext("[a-z]+");
        return (d ? 1 : 0) * 10 + (l ? 1 : 0);
    }

    // next(pattern): read the leading lowercase word, skip the number.
    public static int nextLetters() {
        Scanner sc = new Scanner("hello 99");
        String w = sc.next("[a-z]+");
        return cs(w);
    }

    // reset() restores the default whitespace delimiter.
    public static int resetToWhitespace() {
        Scanner sc = new Scanner("x y");
        sc.useDelimiter(",");
        sc.reset();
        String w = sc.next();
        return cs(w);
    }

    // hasNextBoolean is false when no boolean token remains.
    public static int hasNextNoBool() {
        Scanner sc = new Scanner("cat dog");
        boolean h = sc.hasNextBoolean();
        String t = sc.next();
        return (h ? 1 : 0) * 100 + cs(t);
    }
}
