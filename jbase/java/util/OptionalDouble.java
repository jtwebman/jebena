package java.util;

public final class OptionalDouble {

    private static final OptionalDouble EMPTY = new OptionalDouble();

    private final boolean isPresent;
    private final double value;

    private OptionalDouble() {
        this.isPresent = false;
        this.value = 0.0;
    }

    private OptionalDouble(double value) {
        this.isPresent = true;
        this.value = value;
    }

    public static OptionalDouble empty() {
        return EMPTY;
    }

    public static OptionalDouble of(double value) {
        return new OptionalDouble(value);
    }

    public double getAsDouble() {
        if (!isPresent) {
            throw new NoSuchElementException("No value present");
        }
        return value;
    }

    public boolean isPresent() {
        return isPresent;
    }

    public boolean isEmpty() {
        return !isPresent;
    }

    public double orElse(double other) {
        return isPresent ? value : other;
    }

    public double orElseThrow() {
        if (!isPresent) {
            throw new NoSuchElementException("No value present");
        }
        return value;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof OptionalDouble)) {
            return false;
        }
        OptionalDouble other = (OptionalDouble) obj;
        return (isPresent && other.isPresent)
                ? Double.compare(value, other.value) == 0
                : isPresent == other.isPresent;
    }

    @Override
    public int hashCode() {
        return isPresent ? doubleHash(value) : 0;
    }

    private static int doubleHash(double v) {
        long bits = Double.doubleToLongBits(v);
        return (int) (bits ^ (bits >>> 32));
    }

    @Override
    public String toString() {
        return isPresent
                ? ("OptionalDouble[" + value + "]")
                : "OptionalDouble.empty";
    }
}
