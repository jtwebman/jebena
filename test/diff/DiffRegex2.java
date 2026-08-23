import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Differential coverage for the regex replace API: Matcher.replaceAll /
 * replaceFirst (with $g group references and backslash escapes),
 * Matcher.quoteReplacement, and Pattern.quote. String results are encoded as a
 * rolling char checksum so they can be compared byte-for-byte against real java.
 */
public class DiffRegex2 {

    private static int cs(String s) {
        int acc = 0;
        for (int i = 0; i < s.length(); i++) {
            acc = acc * 31 + s.charAt(i);
        }
        return acc;
    }

    // replaceAll("[0-9]","#") on "a1b2c3" -> "a#b#c#"
    static int replaceAllDigits() {
        return cs(Pattern.compile("[0-9]").matcher("a1b2c3").replaceAll("#"));
    }

    // replaceFirst("[0-9]","#") on "a1b2c3" -> "a#b2c3"
    static int replaceFirstDigit() {
        return cs(Pattern.compile("[0-9]").matcher("a1b2c3").replaceFirst("#"));
    }

    // group swap: "(\w)(\d)" -> "$2$1" on "a1b2" -> "1a2b"
    static int groupSwap() {
        Matcher m = Pattern.compile("(\\w)(\\d)").matcher("a1b2");
        return cs(m.replaceAll("$2$1"));
    }

    // replaceFirst with a single group reference
    static int groupSwapFirst() {
        Matcher m = Pattern.compile("(\\w)(\\d)").matcher("a1b2");
        return cs(m.replaceFirst("$2$1"));
    }

    // Pattern.quote used in a match count: the quoted "a.b*c" must match
    // literally (2 occurrences) and NOT match a regex-style "axbc".
    static int quoteMatch() {
        Pattern p = Pattern.compile(Pattern.quote("a.b*c"));
        Matcher m = p.matcher("a.b*c x a.b*c");
        int n = 0;
        while (m.find()) {
            n++;
        }
        int extra = p.matcher("axbc").find() ? 100 : 0;
        return n + extra; // 2
    }

    // quoteReplacement makes "$5" a literal, not a group reference.
    static int quoteReplLiteral() {
        String r = Matcher.quoteReplacement("$5");
        String out = Pattern.compile("X").matcher("aXb").replaceAll(r);
        return cs(out); // "a$5b"
    }

    // quoteReplacement over a mix of backslash and dollar.
    static int quoteReplMixed() {
        String r = Matcher.quoteReplacement("$1\\n");
        String out = Pattern.compile("o").matcher("foo").replaceAll(r);
        return cs(out); // each 'o' -> "$1\n" literally: "f$1\n$1\n"
    }

    // Backslash escape inside a replacement: "\$" -> literal '$'.
    static int escapedDollar() {
        String out = Pattern.compile("i").matcher("price").replaceAll("\\$");
        return cs(out); // "pr$ce"
    }

    // No match: replaceAll returns the original string.
    static int noMatch() {
        String out = Pattern.compile("[0-9]+").matcher("hello").replaceAll("#");
        return cs(out); // "hello"
    }

    // $0 whole-match reference wrapping every word run.
    static int wholeRef() {
        String out = Pattern.compile("\\w+").matcher("ab cd").replaceAll("[$0]");
        return cs(out); // "[ab] [cd]"
    }
}
