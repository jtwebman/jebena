package java.math;

/**
 * Immutable settings describing a rounding context: a target number of
 * significant digits ({@code precision}) together with a {@link RoundingMode}.
 *
 * <p>A precision of {@code 0} denotes unlimited precision (no rounding).
 *
 * <p>Clean-room implementation from the public contract.
 */
public final class MathContext {

    /** Unlimited precision arithmetic; {@code precision == 0}. */
    public static final MathContext UNLIMITED = new MathContext(0, RoundingMode.HALF_UP);

    /** {@code precision = 7}, {@code roundingMode = HALF_EVEN} (IEEE 754 single). */
    public static final MathContext DECIMAL32 = new MathContext(7, RoundingMode.HALF_EVEN);

    /** {@code precision = 16}, {@code roundingMode = HALF_EVEN} (IEEE 754 double). */
    public static final MathContext DECIMAL64 = new MathContext(16, RoundingMode.HALF_EVEN);

    /** {@code precision = 34}, {@code roundingMode = HALF_EVEN} (IEEE 754 quad). */
    public static final MathContext DECIMAL128 = new MathContext(34, RoundingMode.HALF_EVEN);

    private final int precision;
    private final RoundingMode roundingMode;

    public MathContext(int setPrecision) {
        this(setPrecision, RoundingMode.HALF_UP);
    }

    public MathContext(int setPrecision, RoundingMode setRoundingMode) {
        if (setPrecision < 0) {
            throw new IllegalArgumentException("Digits < 0");
        }
        if (setRoundingMode == null) {
            throw new NullPointerException("null RoundingMode");
        }
        this.precision = setPrecision;
        this.roundingMode = setRoundingMode;
    }

    public int getPrecision() {
        return precision;
    }

    public RoundingMode getRoundingMode() {
        return roundingMode;
    }

    public boolean equals(Object x) {
        if (this == x) {
            return true;
        }
        if (!(x instanceof MathContext)) {
            return false;
        }
        MathContext other = (MathContext) x;
        return this.precision == other.precision
                && this.roundingMode == other.roundingMode;
    }

    public int hashCode() {
        return this.precision + this.roundingMode.hashCode() * 59;
    }

    public String toString() {
        return "precision=" + precision + " roundingMode=" + roundingMode.name();
    }
}
