import java.util.function.Function;

public class DiffStr4 {
    // Control: repeat already exists.
    public static int repeatLenHash() {
        String r = "abc".repeat(3);
        return r.length() * 31 + r.hashCode();
    }

    public static int repeatZero() {
        String r = "xy".repeat(0);
        return r.isEmpty() ? 777 : r.length();
    }

    public static int indentLenLines() {
        String s = "a\nbb\nccc";
        String out = s.indent(4);
        int nl = 0;
        for (int i = 0; i < out.length(); i++) {
            if (out.charAt(i) == '\n') nl++;
        }
        return out.length() * 100 + nl;
    }

    public static int indentNegative() {
        String s = "    x\n  y\nz";
        String out = s.indent(-2);
        int acc = out.length();
        acc = acc * 31 + out.hashCode();
        return acc;
    }

    public static int indentEmpty() {
        String out = "".indent(5);
        return out.isEmpty() ? 42 : out.length();
    }

    public static int stripIndentBasic() {
        String s = "    line1\n    line2\n    line3\n";
        String out = s.stripIndent();
        int nl = 0;
        for (int i = 0; i < out.length(); i++) {
            if (out.charAt(i) == '\n') nl++;
        }
        return out.length() * 100 + nl;
    }

    public static int stripIndentMixed() {
        String s = "      alpha\n    beta\n        gamma\n";
        String out = s.stripIndent();
        return out.length() * 31 + out.hashCode();
    }

    public static int stripIndentNoTrailing() {
        String s = "   one\n   two";
        String out = s.stripIndent();
        return out.length() * 31 + out.hashCode();
    }

    public static int codePointsCount() {
        long c = "Hello, World".codePoints().count();
        return (int) c;
    }

    public static int codePointsSum() {
        return "ABCabc".codePoints().sum();
    }

    public static int codePointsMapped() {
        return "abc".codePoints().map(x -> x + 1).sum();
    }

    public static int transformLength() {
        Object r = "hello world".transform(s -> Integer.valueOf(((String) s).length()));
        return ((Integer) r).intValue();
    }

    public static int transformUpper() {
        Object r = "abc".transform(s -> ((String) s).toUpperCase());
        String u = (String) r;
        return u.length() * 31 + u.hashCode();
    }
}
