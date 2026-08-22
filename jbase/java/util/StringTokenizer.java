package java.util;

/**
 * Clean-room implementation of java.util.StringTokenizer.
 *
 * Splits a string into tokens separated by delimiter characters. A character is
 * a delimiter if it appears in the current delimiter string. When returnDelims
 * is true, delimiter characters are returned as single-character tokens; when
 * false they are skipped and only the text between delimiters is returned.
 */
public class StringTokenizer {
    private int currentPosition;
    private int newPosition;
    private final int maxPosition;
    private final String str;
    private String delimiters;
    private final boolean retDelims;
    private boolean delimsChanged;

    public StringTokenizer(String str, String delim, boolean returnDelims) {
        this.currentPosition = 0;
        this.newPosition = -1;
        this.delimsChanged = false;
        this.str = str;
        this.maxPosition = str.length();
        this.delimiters = delim;
        this.retDelims = returnDelims;
    }

    public StringTokenizer(String str, String delim) {
        this(str, delim, false);
    }

    public StringTokenizer(String str) {
        this(str, " \t\n\r\f", false);
    }

    /**
     * Returns true if ch is one of the current delimiter characters.
     */
    private boolean isDelimiter(char ch) {
        int len = delimiters.length();
        for (int i = 0; i < len; i++) {
            if (delimiters.charAt(i) == ch) {
                return true;
            }
        }
        return false;
    }

    /**
     * Skips past any leading delimiters starting from position and returns the
     * index of the first non-delimiter (or a delimiter itself, when retDelims is
     * true, in which case scanning stops immediately). Returns maxPosition if the
     * remainder of the string is all delimiters (with retDelims false).
     */
    private int skipDelimiters(int position) {
        int pos = position;
        while (!retDelims && pos < maxPosition) {
            if (!isDelimiter(str.charAt(pos))) {
                break;
            }
            pos++;
        }
        return pos;
    }

    /**
     * Scans forward from position to the next delimiter, returning that index (or
     * maxPosition if none remains).
     */
    private int scanToken(int position) {
        int pos = position;
        while (pos < maxPosition) {
            if (isDelimiter(str.charAt(pos))) {
                break;
            }
            pos++;
        }
        if (retDelims && (position == pos)) {
            if (isDelimiter(str.charAt(pos))) {
                pos++;
            }
        }
        return pos;
    }

    public boolean hasMoreTokens() {
        newPosition = skipDelimiters(currentPosition);
        return (newPosition < maxPosition);
    }

    public String nextToken() {
        currentPosition = (newPosition >= 0 && !delimsChanged)
            ? newPosition : skipDelimiters(currentPosition);

        delimsChanged = false;
        newPosition = -1;

        if (currentPosition >= maxPosition) {
            throw new NoSuchElementException();
        }
        int start = currentPosition;
        currentPosition = scanToken(currentPosition);
        return str.substring(start, currentPosition);
    }

    public String nextToken(String delim) {
        delimiters = delim;
        delimsChanged = true;
        return nextToken();
    }

    public boolean hasMoreElements() {
        return hasMoreTokens();
    }

    public Object nextElement() {
        return nextToken();
    }

    public int countTokens() {
        int count = 0;
        int currpos = currentPosition;
        while (currpos < maxPosition) {
            currpos = skipDelimiters(currpos);
            if (currpos >= maxPosition) {
                break;
            }
            currpos = scanToken(currpos);
            count++;
        }
        return count;
    }
}
