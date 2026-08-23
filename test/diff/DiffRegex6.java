import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class DiffRegex6 {

    private static int countFind(Pattern p, String input) {
        Matcher m = p.matcher(input);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    // CASE_INSENSITIVE: "abc" matches "ABC".
    public static int ciMatches() {
        boolean b = Pattern.compile("abc", Pattern.CASE_INSENSITIVE)
            .matcher("ABC").matches();
        return b ? 1 : 0;
    }

    // Without the flag, "abc" does not match "ABC".
    public static int ciNoFlagNoMatch() {
        boolean b = Pattern.compile("abc").matcher("ABC").matches();
        return b ? 1 : 0;
    }

    // CASE_INSENSITIVE with mixed-case pattern and mixed-case input.
    public static int ciMixed() {
        boolean b = Pattern.compile("aBc", Pattern.CASE_INSENSITIVE)
            .matcher("AbC").matches();
        return b ? 1 : 0;
    }

    // MULTILINE: ^abc matches at the start of each line.
    public static int multilineCaretCount() {
        Pattern p = Pattern.compile("^abc", Pattern.MULTILINE);
        return countFind(p, "abc\nabc\nabc");
    }

    // Without MULTILINE, ^abc matches only at the start of input.
    public static int noMultilineCaretCount() {
        Pattern p = Pattern.compile("^abc");
        return countFind(p, "abc\nabc\nabc");
    }

    // MULTILINE: abc$ matches at the end of each line.
    public static int multilineDollarCount() {
        Pattern p = Pattern.compile("abc$", Pattern.MULTILINE);
        return countFind(p, "abc\nabc\nabc");
    }

    // DOTALL: "." matches a newline.
    public static int dotallMatches() {
        boolean b = Pattern.compile("a.c", Pattern.DOTALL)
            .matcher("a\nc").matches();
        return b ? 1 : 0;
    }

    // Without DOTALL, "." does not match a newline.
    public static int noDotallNoMatch() {
        boolean b = Pattern.compile("a.c").matcher("a\nc").matches();
        return b ? 1 : 0;
    }

    // Inline (?i) flag.
    public static int inlineI() {
        boolean b = Pattern.compile("(?i)abc").matcher("ABC").matches();
        return b ? 1 : 0;
    }

    // Inline (?m) flag: ^\w+ per line.
    public static int inlineM() {
        Pattern p = Pattern.compile("(?m)^\\w+");
        return countFind(p, "foo\nbar\nbaz");
    }

    // Inline (?s) flag: "." matches a newline.
    public static int inlineS() {
        boolean b = Pattern.compile("(?s)a.c").matcher("a\nc").matches();
        return b ? 1 : 0;
    }

    // Combined CASE_INSENSITIVE | MULTILINE.
    public static int combinedCiMultiline() {
        Pattern p = Pattern.compile("^abc$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
        return countFind(p, "ABC\nabc\nAbC");
    }
}
