package java.util;

public final class OptionalLong {

    private static final OptionalLong EMPTY = new OptionalLong();

    private final boolean isPresent;
    private final long value;

    private OptionalLong() {
        this.isPresent = false;
        this.value = 0L;
    }

    private OptionalLong(long value) {
        this.isPresent = true;
        this.value = value;
    }

    public static OptionalLong empty() {
        return EMPTY;
    }

    public static OptionalLong of(long value) {
        return new OptionalLong(value);
    }

    public long getAsLong() {
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

    public long orElse(long other) {
        return isPresent ? value : other;
    }

    public long orElseThrow() {
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
        if (!(obj instanceof OptionalLong)) {
            return false;
        }
        OptionalLong other = (OptionalLong) obj;
        return (isPresent && other.isPresent)
                ? value == other.value
                : isPresent == other.isPresent;
    }

    @Override
    public int hashCode() {
        return isPresent ? (int) (value ^ (value >>> 32)) : 0;
    }

    @Override
    public String toString() {
        return isPresent
                ? ("OptionalLong[" + value + "]")
                : "OptionalLong.empty";
    }
}
