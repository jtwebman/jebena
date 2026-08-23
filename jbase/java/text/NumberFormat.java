package java.text;

/**
 * Clean-room implementation of a useful subset of {@code java.text.NumberFormat}.
 *
 * <p>This is intentionally a concrete class rather than the abstract JDK type, so
 * that the common factory entry points can be exercised directly. It is backed by
 * {@link DecimalFormat}: each mutating setter rebuilds a pattern string and installs
 * a fresh backing formatter.
 *
 * <p>Supported factories:
 * <ul>
 *   <li>{@link #getInstance()} / {@link #getNumberInstance()} &mdash; grouping on,
 *       zero minimum and three maximum fraction digits (pattern {@code "#,##0.###"}).</li>
 *   <li>{@link #getIntegerInstance()} &mdash; grouping on, no fraction digits
 *       (pattern {@code "#,##0"}), rounding HALF_EVEN.</li>
 * </ul>
 *
 * <p>Only the grouping flag and the minimum/maximum fraction digit counts are
 * adjustable. Prefixes/suffixes, percent/currency, exponential notation and custom
 * symbols are out of scope.
 */
public class NumberFormat {

    private boolean groupingUsed;
    private int minimumFractionDigits;
    private int maximumFractionDigits;
    private DecimalFormat backing;

    private NumberFormat(boolean grouping, int minFrac, int maxFrac) {
        this.groupingUsed = grouping;
        this.minimumFractionDigits = minFrac;
        this.maximumFractionDigits = maxFrac;
        rebuild();
    }

    public static NumberFormat getInstance() {
        return getNumberInstance();
    }

    public static NumberFormat getNumberInstance() {
        return new NumberFormat(true, 0, 3);
    }

    public static NumberFormat getIntegerInstance() {
        return new NumberFormat(true, 0, 0);
    }

    public String format(long value) {
        return backing.format(value);
    }

    public String format(double value) {
        return backing.format(value);
    }

    public void setMaximumFractionDigits(int newValue) {
        if (newValue < 0) {
            newValue = 0;
        }
        this.maximumFractionDigits = newValue;
        if (this.minimumFractionDigits > this.maximumFractionDigits) {
            this.minimumFractionDigits = this.maximumFractionDigits;
        }
        rebuild();
    }

    public void setMinimumFractionDigits(int newValue) {
        if (newValue < 0) {
            newValue = 0;
        }
        this.minimumFractionDigits = newValue;
        if (this.maximumFractionDigits < this.minimumFractionDigits) {
            this.maximumFractionDigits = this.minimumFractionDigits;
        }
        rebuild();
    }

    public void setGroupingUsed(boolean newValue) {
        this.groupingUsed = newValue;
        rebuild();
    }

    public boolean isGroupingUsed() {
        return groupingUsed;
    }

    public int getMaximumFractionDigits() {
        return maximumFractionDigits;
    }

    public int getMinimumFractionDigits() {
        return minimumFractionDigits;
    }

    /** Rebuild the backing {@link DecimalFormat} from the current state. */
    private void rebuild() {
        StringBuilder pattern = new StringBuilder();
        if (groupingUsed) {
            pattern.append("#,##0");
        } else {
            pattern.append("0");
        }
        if (maximumFractionDigits > 0) {
            pattern.append('.');
            for (int i = 0; i < minimumFractionDigits; i++) {
                pattern.append('0');
            }
            for (int i = minimumFractionDigits; i < maximumFractionDigits; i++) {
                pattern.append('#');
            }
        }
        this.backing = new DecimalFormat(pattern.toString());
    }
}
