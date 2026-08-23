import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class DiffRegex4 {

    private static int checksum(String s) {
        int h = 0;
        for (int i = 0; i < s.length(); i++) {
            h = h * 31 + s.charAt(i);
        }
        return h;
    }

    private static String join(String[] parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append('|');
            }
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    // split(",") on "a,b,,c,," (default): trailing empties removed -> a,b,,c
    public static int splitDefaultJoin() {
        String[] parts = Pattern.compile(",").split("a,b,,c,,");
        return checksum(join(parts));
    }

    // Number of pieces for the default split -> 4
    public static int splitDefaultCount() {
        String[] parts = Pattern.compile(",").split("a,b,,c,,");
        return parts.length;
    }

    // split with limit 2: ["a", "b,,c,,"]
    public static int splitLimit2Join() {
        String[] parts = Pattern.compile(",").split("a,b,,c,,", 2);
        return checksum(join(parts));
    }

    public static int splitLimit2Count() {
        String[] parts = Pattern.compile(",").split("a,b,,c,,", 2);
        return parts.length;
    }

    // split with limit -1: keep trailing empties -> 6 pieces
    public static int splitNegCount() {
        String[] parts = Pattern.compile(",").split("a,b,,c,,", -1);
        return parts.length;
    }

    public static int splitNegJoin() {
        String[] parts = Pattern.compile(",").split("a,b,,c,,", -1);
        return checksum(join(parts));
    }

    // Split on a multi-char regex, trailing empties removed.
    public static int splitRegexJoin() {
        String[] parts = Pattern.compile("\\d+").split("a1b22c333");
        return checksum(join(parts));
    }

    // appendReplacement/appendTail loop replacing \d+ with # in "a1b22c333"
    public static int appendReplaceResult() {
        Pattern p = Pattern.compile("\\d+");
        Matcher m = p.matcher("a1b22c333");
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, "#");
        }
        m.appendTail(sb);
        return checksum(sb.toString());
    }

    public static int appendReplaceLen() {
        Pattern p = Pattern.compile("\\d+");
        Matcher m = p.matcher("a1b22c333");
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, "#");
        }
        m.appendTail(sb);
        return sb.toString().length();
    }

    // appendReplacement with a $N group reference: swap "key=val" -> "val:key".
    public static int appendReplaceGroupRef() {
        Pattern p = Pattern.compile("(\\w+)=(\\w+)");
        Matcher m = p.matcher("a=1 b=22");
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, "$2:$1");
        }
        m.appendTail(sb);
        return checksum(sb.toString());
    }
}
