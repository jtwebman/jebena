import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Differential coverage for the clean-room java.util.regex engine: literals, '.',
 * character classes (ranges + negation), predefined classes \d\w\s, anchors,
 * greedy quantifiers (* + ? {n} {n,m}), alternation, capturing + non-capturing
 * groups, and the Matcher API (matches/lookingAt/find/group/start/end/groupCount).
 * Every method returns a deterministic int checked byte-for-byte against real java.
 */
public class DiffRegex {

    private static int b(boolean v) {
        return v ? 1 : 0;
    }

    // ---- Pattern.matches: whole-input match ----
    static int matchLit() {
        return b(Pattern.matches("abc", "abc")); // 1
    }

    static int matchDot() {
        return b(Pattern.matches("a.c", "axc")) * 10 + b(Pattern.matches("a.c", "ac")); // 10
    }

    static int matchDigit() {
        return b(Pattern.matches("\\d+", "12345")) * 10 + b(Pattern.matches("\\d+", "12a45")); // 10
    }

    static int matchWord() {
        return b(Pattern.matches("\\w+", "ab_12")) * 10 + b(Pattern.matches("\\w+", "ab 12")); // 10
    }

    static int matchSpace() {
        return b(Pattern.matches("a\\s+b", "a \t b")); // 1
    }

    static int matchAlt() {
        return b(Pattern.matches("cat|dog", "dog")) * 10 + b(Pattern.matches("cat|dog", "cow")); // 10
    }

    static int quantStar() {
        return b(Pattern.matches("ab*c", "ac")) * 10 + b(Pattern.matches("ab*c", "abbbc")); // 11
    }

    static int quantPlus() {
        return b(Pattern.matches("ab+c", "ac")) * 10 + b(Pattern.matches("ab+c", "abbc")); // 01
    }

    static int quantOpt() {
        return b(Pattern.matches("colou?r", "color")) * 10 + b(Pattern.matches("colou?r", "colour")); // 11
    }

    static int braceExact() {
        return b(Pattern.matches("a{3}", "aaa")) * 10 + b(Pattern.matches("a{3}", "aa")); // 10
    }

    static int braceRange() {
        return b(Pattern.matches("a{2,4}", "aaa")) * 100
                + b(Pattern.matches("a{2,4}", "a")) * 10
                + b(Pattern.matches("a{2,4}", "aaaaa")); // 100
    }

    static int classRange() {
        return b(Pattern.matches("[a-f]+", "abcdef")) * 10 + b(Pattern.matches("[a-f]+", "abcg")); // 10
    }

    static int classNeg() {
        return b(Pattern.matches("[^0-9]+", "abcABC")) * 10 + b(Pattern.matches("[^0-9]+", "ab3")); // 10
    }

    static int anchors() {
        return b(Pattern.matches("^abc$", "abc")); // 1
    }

    static int nonCapturing() {
        return b(Pattern.matches("(?:ab)+", "ababab")) * 10 + b(Pattern.matches("(?:ab)+", "aba")); // 10
    }

    static int altGroup() {
        return b(Pattern.matches("(cat|dog)s", "dogs")); // 1
    }

    static int dotStarGreedy() {
        return b(Pattern.matches("a.*c", "axxcxxc")); // 1 (greedy backtracks to last c)
    }

    // ---- Matcher.find / group / start / end / groupCount ----
    static int findCount() {
        Matcher m = Pattern.compile("\\d+").matcher("a1b22c333d");
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n; // 3
    }

    static int groupCapture() {
        Matcher m = Pattern.compile("(\\d+)-(\\d+)").matcher("12-345");
        if (!m.matches()) {
            return -1;
        }
        return Integer.parseInt(m.group(1)) + Integer.parseInt(m.group(2)); // 357
    }

    static int startEnd() {
        Matcher m = Pattern.compile("\\d+").matcher("abc123xy");
        if (!m.find()) {
            return -1;
        }
        return m.start() * 100 + m.end(); // 3*100 + 6 = 306
    }

    static int groupCountTest() {
        return Pattern.compile("(a)(b)(c)").matcher("abc").groupCount(); // 3
    }

    static int lookingAt() {
        Matcher m = Pattern.compile("\\d+").matcher("123abc");
        return b(m.lookingAt()) * 10 + b(Pattern.compile("\\d+").matcher("abc123").lookingAt()); // 10
    }

    static int wordCount() {
        Matcher m = Pattern.compile("\\w+").matcher("foo bar  baz quux");
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n; // 4
    }

    static int sumLens() {
        // total matched length of every run of letters
        Matcher m = Pattern.compile("[a-z]+").matcher("ab12cde3fghi");
        int total = 0;
        while (m.find()) {
            total += m.end() - m.start();
        }
        return total; // 2 + 3 + 4 = 9
    }
}
