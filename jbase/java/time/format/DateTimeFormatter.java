package java.time.format;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAccessor;

/**
 * Clean-room minimal date-time formatter.
 *
 * <p>Supports a small subset of the standard pattern letters: runs of
 * {@code y} (year), {@code M} (month), {@code d} (day-of-month),
 * {@code H} (hour-of-day), {@code m} (minute-of-hour) and {@code s}
 * (second-of-minute). Any other character in the pattern is copied to the
 * output verbatim as a literal separator.
 *
 * <p>Each run of a supported letter is rendered as its numeric field value,
 * zero-padded on the left to the length of the run (for example {@code yyyy}
 * gives a four-digit year and {@code MM} a two-digit month).
 *
 * <p>{@link #format} takes a {@code TemporalAccessor} to match the real JDK
 * signature that bytecode is compiled against; it accepts a {@link LocalDate}
 * or a {@link LocalDateTime}.
 */
public final class DateTimeFormatter {

    private final String pattern;

    private DateTimeFormatter(String pattern) {
        this.pattern = pattern;
    }

    public static DateTimeFormatter ofPattern(String pattern) {
        if (pattern == null) {
            throw new IllegalArgumentException("pattern must not be null");
        }
        return new DateTimeFormatter(pattern);
    }

    public String format(TemporalAccessor temporal) {
        if (temporal == null) {
            throw new IllegalArgumentException("temporal must not be null");
        }
        // Cast through Object so instanceof against the final LocalDate/LocalDateTime
        // types compiles even though they do not statically implement TemporalAccessor
        // in jbase.
        Object o = temporal;
        if (o instanceof LocalDate) {
            LocalDate d = (LocalDate) o;
            return build(d.getYear(), d.getMonthValue(), d.getDayOfMonth(),
                    0, 0, 0);
        }
        if (o instanceof LocalDateTime) {
            LocalDateTime dt = (LocalDateTime) o;
            return build(dt.getYear(), dt.getMonthValue(), dt.getDayOfMonth(),
                    dt.getHour(), dt.getMinute(), dt.getSecond());
        }
        throw new IllegalArgumentException("Unsupported temporal type");
    }

    private String build(int year, int month, int day,
                         int hour, int minute, int second) {
        StringBuilder sb = new StringBuilder();
        int len = pattern.length();
        int i = 0;
        while (i < len) {
            char c = pattern.charAt(i);
            if (isFieldLetter(c)) {
                int start = i;
                while (i < len && pattern.charAt(i) == c) {
                    i++;
                }
                int count = i - start;
                int value = fieldValue(c, year, month, day, hour, minute, second);
                appendPadded(sb, value, count);
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    private static boolean isFieldLetter(char c) {
        return c == 'y' || c == 'M' || c == 'd'
                || c == 'H' || c == 'm' || c == 's';
    }

    private static int fieldValue(char c, int year, int month, int day,
                                  int hour, int minute, int second) {
        if (c == 'y') {
            return year;
        }
        if (c == 'M') {
            return month;
        }
        if (c == 'd') {
            return day;
        }
        if (c == 'H') {
            return hour;
        }
        if (c == 'm') {
            return minute;
        }
        return second;
    }

    private static void appendPadded(StringBuilder sb, int value, int width) {
        String digits = Integer.toString(value < 0 ? -value : value);
        if (value < 0) {
            sb.append('-');
        }
        for (int pad = digits.length(); pad < width; pad++) {
            sb.append('0');
        }
        sb.append(digits);
    }
}
