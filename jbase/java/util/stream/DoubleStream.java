package java.util.stream;

import java.util.ArrayList;
import java.util.OptionalDouble;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleFunction;
import java.util.function.DoublePredicate;
import java.util.function.DoubleToIntFunction;
import java.util.function.DoubleToLongFunction;
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

    public IntStream mapToInt(DoubleToIntFunction mapper) {
        int[] out = new int[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = mapper.applyAsInt(data[i]);
        }
        return IntStream.of(out);
    }

    public LongStream mapToLong(DoubleToLongFunction mapper) {
        long[] out = new long[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = mapper.applyAsLong(data[i]);
        }
        return LongStream.of(out);
    }

    public Stream mapToObj(DoubleFunction mapper) {
        ArrayList list = new ArrayList();
        for (int i = 0; i < data.length; i++) {
            list.add(mapper.apply(data[i]));
        }
        return new Stream(list);
    }

    public DoubleStream distinct() {
        double[] tmp = new double[data.length];
        int n = 0;
        for (int i = 0; i < data.length; i++) {
            boolean seen = false;
            for (int j = 0; j < n; j++) {
                if (Double.compare(tmp[j], data[i]) == 0) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                tmp[n++] = data[i];
            }
        }
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            out[i] = tmp[i];
        }
        return new DoubleStream(out);
    }

    public DoubleStream sorted() {
        double[] out = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = data[i];
        }
        for (int i = 1; i < out.length; i++) {
            double key = out[i];
            int j = i - 1;
            while (j >= 0 && Double.compare(out[j], key) > 0) {
                out[j + 1] = out[j];
                j--;
            }
            out[j + 1] = key;
        }
        return new DoubleStream(out);
    }

    public DoubleStream limit(long maxSize) {
        if (maxSize < 0) {
            throw new IllegalArgumentException(Long.toString(maxSize));
        }
        int length = data.length;
        if (maxSize < length) {
            length = (int) maxSize;
        }
        double[] out = new double[length];
        for (int i = 0; i < length; i++) {
            out[i] = data[i];
        }
        return new DoubleStream(out);
    }

    public DoubleStream skip(long n) {
        if (n < 0) {
            throw new IllegalArgumentException(Long.toString(n));
        }
        int start = data.length;
        if (n < start) {
            start = (int) n;
        }
        int length = data.length - start;
        double[] out = new double[length];
        for (int i = 0; i < length; i++) {
            out[i] = data[start + i];
        }
        return new DoubleStream(out);
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
