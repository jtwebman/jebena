package java.util;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import java.util.stream.DoubleStream;

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

    public void ifPresent(DoubleConsumer action) {
        if (isPresent) {
            action.accept(value);
        }
    }

    public void ifPresentOrElse(DoubleConsumer action, Runnable emptyAction) {
        if (isPresent) {
            action.accept(value);
        } else {
            emptyAction.run();
        }
    }

    public DoubleStream stream() {
        if (isPresent) {
            return DoubleStream.of(value);
        } else {
            return DoubleStream.of();
        }
    }

    public double orElse(double other) {
        return isPresent ? value : other;
    }

    public double orElseGet(DoubleSupplier supplier) {
        return isPresent ? value : supplier.getAsDouble();
    }

    public double orElseThrow() {
        if (!isPresent) {
            throw new NoSuchElementException("No value present");
        }
        return value;
    }

    public double orElseThrow(Supplier exceptionSupplier) throws Throwable {
        if (isPresent) {
            return value;
        }
        throw (Throwable) exceptionSupplier.get();
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
