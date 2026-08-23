import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class DiffRegex3 {

    // "2026-03-09" with named groups y/m/d -> group("y") as int
    public static int namedY() {
        Pattern p = Pattern.compile("(?<y>\\d{4})-(?<m>\\d{2})-(?<d>\\d{2})");
        Matcher m = p.matcher("2026-03-09");
        if (!m.matches()) return -1;
        return Integer.parseInt(m.group("y"));
    }

    public static int namedM() {
        Pattern p = Pattern.compile("(?<y>\\d{4})-(?<m>\\d{2})-(?<d>\\d{2})");
        Matcher m = p.matcher("2026-03-09");
        if (!m.matches()) return -1;
        return Integer.parseInt(m.group("m"));
    }

    public static int namedD() {
        Pattern p = Pattern.compile("(?<y>\\d{4})-(?<m>\\d{2})-(?<d>\\d{2})");
        Matcher m = p.matcher("2026-03-09");
        if (!m.matches()) return -1;
        return Integer.parseInt(m.group("d"));
    }

    // Named group value equals positional group value (checksum of the string).
    public static int namedEqualsPositional() {
        Pattern p = Pattern.compile("(?<y>\\d{4})-(?<m>\\d{2})-(?<d>\\d{2})");
        Matcher m = p.matcher("2026-03-09");
        if (!m.matches()) return -1;
        boolean ok = m.group("y").equals(m.group(1))
            && m.group("m").equals(m.group(2))
            && m.group("d").equals(m.group(3));
        return ok ? 1 : 0;
    }

    // start(2)/end(2) positions on the date pattern (group 2 = month).
    public static int startEndGroup2() {
        Pattern p = Pattern.compile("(?<y>\\d{4})-(?<m>\\d{2})-(?<d>\\d{2})");
        Matcher m = p.matcher("2026-03-09");
        if (!m.matches()) return -1;
        // start(2)=5, end(2)=7 -> encode as start*100+end
        return m.start(2) * 100 + m.end(2);
    }

    // start/end by name should equal start/end by number.
    public static int startEndByName() {
        Pattern p = Pattern.compile("(?<y>\\d{4})-(?<m>\\d{2})-(?<d>\\d{2})");
        Matcher m = p.matcher("2026-03-09");
        if (!m.matches()) return -1;
        boolean ok = m.start("m") == m.start(2) && m.end("d") == m.end(3);
        return ok ? 1 : 0;
    }

    // "abc123" with ([a-z]+)(\d+): start(1)+end(1)+start(2)+end(2)
    public static int positionalStartEnd() {
        Pattern p = Pattern.compile("([a-z]+)(\\d+)");
        Matcher m = p.matcher("abc123");
        if (!m.matches()) return -1;
        // 0 + 3 + 3 + 6 = 12
        return m.start(1) + m.end(1) + m.start(2) + m.end(2);
    }

    // find() with named groups on a longer string; group("m") after find.
    public static int findNamed() {
        Pattern p = Pattern.compile("(?<y>\\d{4})-(?<m>\\d{2})-(?<d>\\d{2})");
        Matcher m = p.matcher("date: 1999-12-31 end");
        if (!m.find()) return -1;
        return Integer.parseInt(m.group("m")) * 10000
            + m.start("y") * 100 + m.end("d");
    }
}
