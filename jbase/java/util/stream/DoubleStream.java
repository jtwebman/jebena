package java.util.stream;

import java.util.ArrayList;
import java.util.OptionalDouble;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoublePredicate;
import java.util.function.DoubleUnaryOperator;

/**
 * Clean-room, EAGER primitive double stream backed by a {@code double[]}. Every
 * intermediate operation fully realizes its result into a fresh {@code double[]}
 * and returns a new {@code DoubleStream}.
 */
public class DoubleStream {

    private final double[] data;

    private DoubleStream(double[] values) {
        this.data = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            this.data[i] = values[i];
        }
    }

    public static DoubleStream of(double... values) {
        if (values == null) {
            return new DoubleStream(new double[0]);
        }
        return new DoubleStream(values);
    }

    public double sum() {
        double total = 0.0;
        for (int i = 0; i < data.length; i++) {
            total += data[i];
        }
        return total;
    }

    public long count() {
        return data.length;
    }

    public DoubleStream map(DoubleUnaryOperator mapper) {
        double[] out = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = mapper.applyAsDouble(data[i]);
        }
        return new DoubleStream(out);
    }

    public DoubleStream filter(DoublePredicate predicate) {
        double[] tmp = new double[data.length];
        int n = 0;
        for (int i = 0; i < data.length; i++) {
            if (predicate.test(data[i])) {
                tmp[n++] = data[i];
            }
        }
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            out[i] = tmp[i];
        }
        return new DoubleStream(out);
    }

    public double reduce(double identity, DoubleBinaryOperator op) {
        double result = identity;
        for (int i = 0; i < data.length; i++) {
            result = op.applyAsDouble(result, data[i]);
        }
        return result;
    }

    public OptionalDouble min() {
        if (data.length == 0) {
            return OptionalDouble.empty();
        }
        double m = data[0];
        for (int i = 1; i < data.length; i++) {
            if (data[i] < m) {
                m = data[i];
            }
        }
        return OptionalDouble.of(m);
    }

    public OptionalDouble max() {
        if (data.length == 0) {
            return OptionalDouble.empty();
        }
        double m = data[0];
        for (int i = 1; i < data.length; i++) {
            if (data[i] > m) {
                m = data[i];
            }
        }
        return OptionalDouble.of(m);
    }

    public OptionalDouble average() {
        if (data.length == 0) {
            return OptionalDouble.empty();
        }
        double total = 0.0;
        for (int i = 0; i < data.length; i++) {
            total += data[i];
        }
        return OptionalDouble.of(total / (double) data.length);
    }

    /** Boxes each double into a Double and returns an object Stream. */
    public Stream boxed() {
        ArrayList list = new ArrayList();
        for (int i = 0; i < data.length; i++) {
            list.add(Double.valueOf(data[i]));
        }
        return new Stream(list);
    }

    public double[] toArray() {
        double[] out = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = data[i];
        }
        return out;
    }
}
