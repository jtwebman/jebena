package java.util;

public final class OptionalInt {

    private static final OptionalInt EMPTY = new OptionalInt();

    private final boolean isPresent;
    private final int value;

    private OptionalInt() {
        this.isPresent = false;
        this.value = 0;
    }

    private OptionalInt(int value) {
        this.isPresent = true;
        this.value = value;
    }

    public static OptionalInt empty() {
        return EMPTY;
    }

    public static OptionalInt of(int value) {
        return new OptionalInt(value);
    }

    public int getAsInt() {
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

    public int orElse(int other) {
        return isPresent ? value : other;
    }

    public int orElseThrow() {
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
        if (!(obj instanceof OptionalInt)) {
            return false;
        }
        OptionalInt other = (OptionalInt) obj;
        return (isPresent && other.isPresent)
                ? value == other.value
                : isPresent == other.isPresent;
    }

    @Override
    public int hashCode() {
        return isPresent ? value : 0;
    }

    @Override
    public String toString() {
        return isPresent
                ? ("OptionalInt[" + value + "]")
                : "OptionalInt.empty";
    }
}
