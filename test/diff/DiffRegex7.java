import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class DiffRegex7 {

    // Encode a split result: (fieldCount * 100000) + checksum, where checksum
    // sums (char + 1) over every character of every field, weighted by field
    // index so field order matters.
    private static int encode(String[] parts) {
        int sum = 0;
        for (int i = 0; i < parts.length; i++) {
            String f = parts[i];
            int fieldSum = 0;
            for (int k = 0; k < f.length(); k++) {
                fieldSum += (f.charAt(k) + 1);
            }
            sum += fieldSum * (i + 1);
        }
        return parts.length * 100000 + sum;
    }

    // Positive limit: trailing fields preserved, remainder absorbed into last.
    public static int splitPosLimit() {
        String[] parts = Pattern.compile(",").split("a,b,c,,", 2);
        return encode(parts);
    }

    // Positive limit larger than needed pieces.
    public static int splitPosLimitBig() {
        String[] parts = Pattern.compile(",").split("a,b,c", 10);
        return encode(parts);
    }

    // limit == 0: trailing empty strings removed.
    public static int splitZeroLimit() {
        String[] parts = Pattern.compile(",").split("a,b,c,,", 0);
        return encode(parts);
    }

    // Default split (== limit 0): trailing empties removed.
    public static int splitDefault() {
        String[] parts = Pattern.compile(",").split("x,y,,,");
        return encode(parts);
    }

    // Negative limit: all trailing empties kept.
    public static int splitNegLimit() {
        String[] parts = Pattern.compile(",").split("a,b,c,,", -1);
        return encode(parts);
    }

    // groupCount() with nested capturing groups.
    public static int groupCountNested() {
        Pattern p = Pattern.compile("(a(b)c)(d)");
        return p.matcher("abcd").matches() ? p.matcher("abcd").groupCount() : -1;
    }

    // Nested group content lengths: group(1) = "abc" (3), group(2) = "b" (1).
    public static int nestedGroupLens() {
        Matcher m = Pattern.compile("(a(b)c)").matcher("abc");
        if (!m.matches()) {
            return -1;
        }
        return m.group(1).length() * 100 + m.group(2).length();
    }

    // start()/end() of an inner group inside a larger match.
    public static int groupStartEnd() {
        Matcher m = Pattern.compile("x(\\d+)y").matcher("x12345y");
        if (!m.matches()) {
            return -1;
        }
        // start(1)=1, end(1)=6, group(1).length()=5
        return m.start(1) * 10000 + m.end(1) * 100 + m.group(1).length();
    }

    // find() locating a group in the middle of a longer string.
    public static int findGroupBounds() {
        Matcher m = Pattern.compile("(\\d+)").matcher("abc789def");
        if (!m.find()) {
            return -1;
        }
        return m.start(1) * 1000 + m.end(1) * 10 + m.group(1).length();
    }

    // replaceAll with a backreference to a captured group.
    public static int replaceBackref() {
        String out = Pattern.compile("(\\w)(\\w)")
            .matcher("abcd").replaceAll("$2$1");
        int sum = out.length() * 1000;
        for (int i = 0; i < out.length(); i++) {
            sum += out.charAt(i);
        }
        return sum;
    }

    // replaceAll swapping two named-ish numeric groups around a separator.
    public static int replaceSwapPairs() {
        String out = Pattern.compile("(\\d+)-(\\d+)")
            .matcher("12-34 and 56-78").replaceAll("$2-$1");
        int sum = out.length() * 1000;
        for (int i = 0; i < out.length(); i++) {
            sum += out.charAt(i);
        }
        return sum;
    }
}
