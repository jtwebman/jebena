package java.util;

/**
 * Clean-room implementation of java.util.Scanner over an in-memory String
 * source only (no InputStream). Splits the source into tokens separated by runs
 * of whitespace (as defined by Character.isWhitespace) and offers typed reads.
 *
 * Only the String-source constructor is supported. Numeric parsing for int and
 * long delegates to Integer.parseInt / Long.parseLong; double parsing is done
 * privately here because java.lang.Double.parseDouble is not part of jbase.
 */
public class Scanner {
    private final String src;
    private final int len;
    private int pos;
    private boolean closed;

    public Scanner(String source) {
        if (source == null) {
            throw new NullPointerException("source");
        }
        this.src = source;
        this.len = source.length();
        this.pos = 0;
        this.closed = false;
    }

    private int skipWhitespace(int p) {
        int i = p;
        while (i < len && Character.isWhitespace(src.charAt(i))) {
            i++;
        }
        return i;
    }

    private int tokenEnd(int p) {
        int i = p;
        while (i < len && !Character.isWhitespace(src.charAt(i))) {
            i++;
        }
        return i;
    }

    /**
     * Returns the next whitespace-delimited token without advancing, or null if
     * none remains.
     */
    private String peekToken() {
        int start = skipWhitespace(pos);
        if (start >= len) {
            return null;
        }
        int end = tokenEnd(start);
        return src.substring(start, end);
    }

    public boolean hasNext() {
        return peekToken() != null;
    }

    public String next() {
        int start = skipWhitespace(pos);
        if (start >= len) {
            throw new NoSuchElementException();
        }
        int end = tokenEnd(start);
        pos = end;
        return src.substring(start, end);
    }

    private static boolean isIntToken(String t) {
        try {
            Integer.parseInt(t);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isLongToken(String t) {
        try {
            Long.parseLong(t);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public boolean hasNextInt() {
        String t = peekToken();
        return t != null && isIntToken(t);
    }

    public int nextInt() {
        String t = peekToken();
        if (t == null) {
            throw new NoSuchElementException();
        }
        int value = Integer.parseInt(t);
        next();
        return value;
    }

    public boolean hasNextLong() {
        String t = peekToken();
        return t != null && isLongToken(t);
    }

    public long nextLong() {
        String t = peekToken();
        if (t == null) {
            throw new NoSuchElementException();
        }
        long value = Long.parseLong(t);
        next();
        return value;
    }

    public boolean hasNextDouble() {
        String t = peekToken();
        return t != null && validDouble(t);
    }

    public double nextDouble() {
        String t = peekToken();
        if (t == null) {
            throw new NoSuchElementException();
        }
        if (!validDouble(t)) {
            throw new NumberFormatException(t);
        }
        double value = parseDoubleImpl(t);
        next();
        return value;
    }

    /**
     * Recognizes an optionally signed decimal number with an optional fractional
     * part and optional decimal exponent. At least one digit must be present.
     */
    private static boolean validDouble(String t) {
        int n = t.length();
        if (n == 0) {
            return false;
        }
        int i = 0;
        char c0 = t.charAt(0);
        if (c0 == '+' || c0 == '-') {
            i++;
        }
        boolean digits = false;
        while (i < n && t.charAt(i) >= '0' && t.charAt(i) <= '9') {
            digits = true;
            i++;
        }
        if (i < n && t.charAt(i) == '.') {
            i++;
            while (i < n && t.charAt(i) >= '0' && t.charAt(i) <= '9') {
                digits = true;
                i++;
            }
        }
        if (!digits) {
            return false;
        }
        if (i < n && (t.charAt(i) == 'e' || t.charAt(i) == 'E')) {
            i++;
            if (i < n && (t.charAt(i) == '+' || t.charAt(i) == '-')) {
                i++;
            }
            boolean expDigits = false;
            while (i < n && t.charAt(i) >= '0' && t.charAt(i) <= '9') {
                expDigits = true;
                i++;
            }
            if (!expDigits) {
                return false;
            }
        }
        return i == n;
    }

    private static double pow10(int e) {
        double r = 1.0;
        for (int k = 0; k < e; k++) {
            r *= 10.0;
        }
        return r;
    }

    /**
     * Parses a validated decimal double. Exactly representable inputs (integers,
     * halves, quarters, and similar) round to the same double as a correctly
     * rounded parser.
     */
    private static double parseDoubleImpl(String t) {
        int n = t.length();
        int i = 0;
        boolean neg = false;
        char c0 = t.charAt(0);
        if (c0 == '+' || c0 == '-') {
            neg = c0 == '-';
            i++;
        }
        double result = 0.0;
        while (i < n && t.charAt(i) >= '0' && t.charAt(i) <= '9') {
            result = result * 10.0 + (t.charAt(i) - '0');
            i++;
        }
        if (i < n && t.charAt(i) == '.') {
            i++;
            double frac = 0.0;
            double scale = 1.0;
            while (i < n && t.charAt(i) >= '0' && t.charAt(i) <= '9') {
                frac = frac * 10.0 + (t.charAt(i) - '0');
                scale *= 10.0;
                i++;
            }
            result += frac / scale;
        }
        if (i < n && (t.charAt(i) == 'e' || t.charAt(i) == 'E')) {
            i++;
            boolean eneg = false;
            if (i < n && (t.charAt(i) == '+' || t.charAt(i) == '-')) {
                eneg = t.charAt(i) == '-';
                i++;
            }
            int exp = 0;
            while (i < n && t.charAt(i) >= '0' && t.charAt(i) <= '9') {
                exp = exp * 10 + (t.charAt(i) - '0');
                i++;
            }
            double p = pow10(exp);
            result = eneg ? result / p : result * p;
        }
        return neg ? -result : result;
    }

    public boolean hasNextLine() {
        return pos < len;
    }

    /**
     * Returns the rest of the current line, excluding the line separator, and
     * advances past that separator. Recognizes \r\n, \n, and \r.
     */
    public String nextLine() {
        if (pos >= len) {
            throw new NoSuchElementException("No line found");
        }
        int i = pos;
        while (i < len) {
            char c = src.charAt(i);
            if (c == '\n' || c == '\r') {
                break;
            }
            i++;
        }
        String line = src.substring(pos, i);
        if (i < len) {
            char c = src.charAt(i);
            if (c == '\r' && i + 1 < len && src.charAt(i + 1) == '\n') {
                pos = i + 2;
            } else {
                pos = i + 1;
            }
        } else {
            pos = i;
        }
        return line;
    }

    public void close() {
        closed = true;
    }
}
