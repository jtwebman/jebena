package java.util.regex;

/**
 * Clean-room, minimal {@code java.util.regex.Matcher}.
 *
 * <p>A matcher holds the input and the mutable state (capture-group bounds,
 * search cursor) used while running the compiled {@link Pattern} node chain
 * against that input. It mirrors the real {@code java.util.regex.Matcher} for
 * the supported subset: {@link #matches()}, {@link #lookingAt()},
 * {@link #find()} (advancing through successive non-overlapping matches),
 * {@link #find(int)}, and the group/start/end accessors. As in the real engine,
 * an unmatched optional group reports {@code null} from {@link #group(int)} and
 * {@code -1} from {@link #start(int)}/{@link #end(int)}.
 */
public final class Matcher {

    private final Pattern parent;
    final String text;
    final int from;
    final int to;

    // Capture bounds; index 0 is the whole match. -1 means "did not participate".
    final int[] groupStarts;
    final int[] groupEnds;

    // Set by matches(): the terminal node then requires i == end of input.
    boolean requireEndAnchor;

    // Where the next find() begins scanning.
    private int searchFrom;

    // Whether the most recent match attempt succeeded (gates the accessors).
    private boolean matched;

    // Cursor of already-consumed input used by the replace* / append operations.
    private int lastAppendPosition;

    Matcher(Pattern parent, CharSequence input) {
        this.parent = parent;
        this.text = input.toString();
        this.from = 0;
        this.to = this.text.length();
        int n = parent.groupCount + 1;
        this.groupStarts = new int[n];
        this.groupEnds = new int[n];
        this.searchFrom = 0;
        this.matched = false;
        this.lastAppendPosition = 0;
    }

    /**
     * Resets this matcher, discarding all match state so that the next search
     * begins at the start of the input.
     */
    public Matcher reset() {
        searchFrom = 0;
        matched = false;
        lastAppendPosition = 0;
        clearGroups();
        return this;
    }

    // ------------------------------------------------------------------
    // Matching operations
    // ------------------------------------------------------------------

    /** Attempts to match the entire input against the pattern. */
    public boolean matches() {
        return matchAt(from, true);
    }

    /** Attempts to match the pattern starting at the beginning of the input. */
    public boolean lookingAt() {
        return matchAt(from, false);
    }

    /**
     * Finds the next subsequence of the input that matches the pattern. Successive
     * calls advance through the input, returning non-overlapping matches.
     */
    public boolean find() {
        int start = searchFrom;
        if (start < 0) {
            start = 0;
        }
        for (int s = start; s <= to; s++) {
            if (matchAt(s, false)) {
                int e = groupEnds[0];
                // Advance past the match; step one char on an empty match so a
                // repeated find() makes progress instead of looping.
                searchFrom = (e > s) ? e : e + 1;
                return true;
            }
        }
        matched = false;
        searchFrom = to + 1;
        return false;
    }

    /** Resets this matcher and then finds the next match at or after {@code start}. */
    public boolean find(int start) {
        if (start < 0 || start > to) {
            throw new IndexOutOfBoundsException("Illegal start index");
        }
        searchFrom = start;
        return find();
    }

    /** Runs the compiled pattern at a fixed start position. */
    private boolean matchAt(int start, boolean anchorEnd) {
        clearGroups();
        requireEndAnchor = anchorEnd;
        groupStarts[0] = start;
        boolean b = parent.root.match(this, start);
        matched = b;
        if (!b) {
            groupStarts[0] = -1;
        }
        return b;
    }

    private void clearGroups() {
        for (int k = 0; k < groupStarts.length; k++) {
            groupStarts[k] = -1;
            groupEnds[k] = -1;
        }
    }

    // ------------------------------------------------------------------
    // Result accessors
    // ------------------------------------------------------------------

    /** The number of capturing groups in the pattern (group 0 excluded). */
    public int groupCount() {
        return parent.groupCount;
    }

    /** The text matched by the whole pattern (group 0). */
    public String group() {
        return group(0);
    }

    /** The text matched by the given group, or {@code null} if it did not match. */
    public String group(int group) {
        ensureMatch();
        checkGroup(group);
        int s = groupStarts[group];
        int e = groupEnds[group];
        if (s < 0 || e < 0) {
            return null;
        }
        return text.substring(s, e);
    }

    /**
     * The text matched by the named group {@code (?<name>...)}, or {@code null}
     * if that group did not participate in the match.
     */
    public String group(String name) {
        if (name == null) {
            throw new NullPointerException("Null group name");
        }
        ensureMatch();
        return group(parent.groupNumberForName(name));
    }

    /** The start index of the named group, or {@code -1} if it did not match. */
    public int start(String name) {
        if (name == null) {
            throw new NullPointerException("Null group name");
        }
        ensureMatch();
        return start(parent.groupNumberForName(name));
    }

    /** The end index of the named group, or {@code -1} if it did not match. */
    public int end(String name) {
        if (name == null) {
            throw new NullPointerException("Null group name");
        }
        ensureMatch();
        return end(parent.groupNumberForName(name));
    }

    /** The start index of the whole match (group 0). */
    public int start() {
        return start(0);
    }

    /** The start index of the given group, or {@code -1} if it did not match. */
    public int start(int group) {
        ensureMatch();
        checkGroup(group);
        return groupStarts[group];
    }

    /** The offset after the last character of the whole match (group 0). */
    public int end() {
        return end(0);
    }

    /** The end index of the given group, or {@code -1} if it did not match. */
    public int end(int group) {
        ensureMatch();
        checkGroup(group);
        return groupEnds[group];
    }

    /** The source pattern this matcher uses. */
    public Pattern pattern() {
        return parent;
    }

    // ------------------------------------------------------------------
    // Replacement operations
    // ------------------------------------------------------------------

    /**
     * Replaces every subsequence of the input that matches the pattern with the
     * given replacement string. The replacement may reference captured groups
     * with {@code $g} and may escape characters with a backslash.
     */
    public String replaceAll(String replacement) {
        reset();
        boolean result = find();
        if (result) {
            StringBuilder sb = new StringBuilder();
            do {
                appendReplacement(sb, replacement);
                result = find();
            } while (result);
            appendTail(sb);
            return sb.toString();
        }
        return text;
    }

    /**
     * Replaces the first subsequence of the input that matches the pattern with
     * the given replacement string, using the same {@code $g}/backslash syntax
     * as {@link #replaceAll(String)}.
     */
    public String replaceFirst(String replacement) {
        if (replacement == null) {
            throw new NullPointerException("replacement");
        }
        reset();
        if (!find()) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        appendReplacement(sb, replacement);
        appendTail(sb);
        return sb.toString();
    }

    /**
     * Appends the input preceding the current match, followed by the expanded
     * replacement, to {@code sb}, then advances the append cursor to the end of
     * the current match.
     */
    private void appendReplacement(StringBuilder sb, String replacement) {
        ensureMatch();
        sb.append(text.substring(lastAppendPosition, start()));

        int cursor = 0;
        int n = replacement.length();
        while (cursor < n) {
            char c = replacement.charAt(cursor);
            if (c == '\\') {
                cursor++;
                if (cursor >= n) {
                    throw new IllegalArgumentException(
                        "character to be escaped is missing");
                }
                sb.append(replacement.charAt(cursor));
                cursor++;
            } else if (c == '$') {
                cursor++;
                if (cursor >= n) {
                    throw new IllegalArgumentException(
                        "Illegal group reference: group index is missing");
                }
                char d = replacement.charAt(cursor);
                if (d < '0' || d > '9') {
                    throw new IllegalArgumentException("Illegal group reference");
                }
                // The first digit always begins a group reference; keep taking
                // digits while the accumulated number stays a valid group.
                int refNum = d - '0';
                cursor++;
                while (cursor < n) {
                    char nd = replacement.charAt(cursor);
                    if (nd < '0' || nd > '9') {
                        break;
                    }
                    int candidate = refNum * 10 + (nd - '0');
                    if (candidate > groupCount()) {
                        break;
                    }
                    refNum = candidate;
                    cursor++;
                }
                String g = group(refNum);
                if (g != null) {
                    sb.append(g);
                }
            } else {
                sb.append(c);
                cursor++;
            }
        }
        lastAppendPosition = end();
    }

    /** Appends the input following the last match to {@code sb}. */
    private void appendTail(StringBuilder sb) {
        sb.append(text.substring(lastAppendPosition, to));
    }

    /**
     * Returns a literal replacement string for the given string. Backslashes and
     * dollar signs are escaped so the result carries no special meaning in
     * {@link #replaceAll(String)} / {@link #replaceFirst(String)}.
     */
    public static String quoteReplacement(String s) {
        if (s.indexOf('\\') < 0 && s.indexOf('$') < 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' || c == '$') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private void ensureMatch() {
        if (!matched) {
            throw new IllegalStateException("No match available");
        }
    }

    private void checkGroup(int group) {
        if (group < 0 || group > parent.groupCount) {
            throw new IndexOutOfBoundsException("No group " + group);
        }
    }
}
