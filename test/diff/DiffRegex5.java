import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.regex.MatchResult;
import java.util.function.Function;

public class DiffRegex5 {

    private static int checksum(String s) {
        int h = 0;
        for (int i = 0; i < s.length(); i++) {
            h = h * 31 + s.charAt(i);
        }
        return h;
    }

    // replaceAll(Function): each digit run replaced by "<len>" -> "a<1>b<2>c<3>"
    public static int digitLengths() {
        Matcher m = Pattern.compile("[0-9]+").matcher("a1b22c333");
        String out = m.replaceAll(new Function<MatchResult, String>() {
            public String apply(MatchResult r) {
                return "<" + r.group().length() + ">";
            }
        });
        return checksum(out);
    }

    // Wrap each match with parentheses -> "a(1)b(22)c(333)"
    public static int wrapMatches() {
        Matcher m = Pattern.compile("[0-9]+").matcher("a1b22c333");
        String out = m.replaceAll(new Function<MatchResult, String>() {
            public String apply(MatchResult r) {
                return "(" + r.group() + ")";
            }
        });
        return checksum(out);
    }

    // Replace each digit run with its start index -> "a1b3c6"
    public static int usePosition() {
        Matcher m = Pattern.compile("[0-9]+").matcher("a1b22c333");
        String out = m.replaceAll(new Function<MatchResult, String>() {
            public String apply(MatchResult r) {
                return String.valueOf(r.start());
            }
        });
        return checksum(out);
    }

    // Replace each digit run with its end index -> "a2b5c9"
    public static int useEnd() {
        Matcher m = Pattern.compile("[0-9]+").matcher("a1b22c333");
        String out = m.replaceAll(new Function<MatchResult, String>() {
            public String apply(MatchResult r) {
                return String.valueOf(r.end());
            }
        });
        return checksum(out);
    }

    // Swap captured groups: (letter)(digits) -> digits+letter -> "1a22b333c"
    public static int swapGroups() {
        Matcher m = Pattern.compile("([a-z])([0-9]+)").matcher("a1b22c333");
        String out = m.replaceAll(new Function<MatchResult, String>() {
            public String apply(MatchResult r) {
                return r.group(2) + r.group(1);
            }
        });
        return checksum(out);
    }

    // Replace each match with the pattern's group count -> "222"
    public static int groupCountReplace() {
        Matcher m = Pattern.compile("([a-z])([0-9]+)").matcher("a1b22c333");
        String out = m.replaceAll(new Function<MatchResult, String>() {
            public String apply(MatchResult r) {
                return String.valueOf(r.groupCount());
            }
        });
        return checksum(out);
    }

    // No match: input returned unchanged -> "a1b22c333"
    public static int noMatch() {
        Matcher m = Pattern.compile("z+").matcher("a1b22c333");
        String out = m.replaceAll(new Function<MatchResult, String>() {
            public String apply(MatchResult r) {
                return "!";
            }
        });
        return checksum(out);
    }
}
