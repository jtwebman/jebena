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
