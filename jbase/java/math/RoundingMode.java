package java.math;

/**
 * Rounding behaviour for numeric operations that must discard precision.
 *
 * <p>Clean-room implementation. This is hand-rolled as a small set of singleton
 * constants (rather than the {@code enum} keyword) because the jbase
 * {@link java.lang.Enum} base is non-generic and javac's enum lowering emits a
 * parameterised {@code Enum<E>} supertype it cannot model, while javac also
 * forbids extending {@code java.lang.Enum} directly. The public surface needed
 * by the current BigDecimal slice — identity-comparable named constants — is
 * preserved. Only the constants required so far are provided.
 */
public final class RoundingMode {

    public static final RoundingMode UP = new RoundingMode("UP", 0);
    public static final RoundingMode DOWN = new RoundingMode("DOWN", 1);
    public static final RoundingMode HALF_UP = new RoundingMode("HALF_UP", 2);
    public static final RoundingMode HALF_EVEN = new RoundingMode("HALF_EVEN", 3);

    private final String name;
    private final int ordinal;

    private RoundingMode(String name, int ordinal) {
        this.name = name;
        this.ordinal = ordinal;
    }

    public String name() {
        return name;
    }

    public int ordinal() {
        return ordinal;
    }

    public String toString() {
        return name;
    }
}
