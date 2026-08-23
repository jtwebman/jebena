package java.util.regex;

/**
 * Clean-room, minimal {@code java.util.regex.MatchResult}.
 *
 * <p>The read-only result of a match against a regular expression: the bounds
 * and text of the whole match (group 0) and of each capturing group. A live
 * {@link Matcher} implements this interface so it can be handed directly to the
 * {@link Matcher#replaceAll(java.util.function.Function)} replacer, mirroring
 * the real {@code java.util.regex.MatchResult} for the supported subset.
 */
public interface MatchResult {

    /** The start index of the whole match (group 0). */
    int start();

    /** The start index of the given group, or {@code -1} if it did not match. */
    int start(int group);

    /** The offset after the last character of the whole match (group 0). */
    int end();

    /** The end index of the given group, or {@code -1} if it did not match. */
    int end(int group);

    /** The text matched by the whole pattern (group 0). */
    String group();

    /** The text matched by the given group, or {@code null} if it did not match. */
    String group(int group);

    /** The number of capturing groups in the pattern (group 0 excluded). */
    int groupCount();
}
