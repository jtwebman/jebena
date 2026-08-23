package java.util.stream;

import java.util.ArrayList;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.IntBinaryOperator;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.IntToDoubleFunction;
import java.util.function.IntToLongFunction;
import java.util.function.IntUnaryOperator;

/**
 * Clean-room, EAGER primitive int stream backed by an {@code int[]}. Every
 * intermediate operation fully realizes its result into a fresh {@code int[]}
 * and returns a new {@code IntStream}.
 */
public class IntStream {

    private final int[] data;

    private IntStream(int[] values) {
        this.data = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            this.data[i] = values[i];
        }
    }

    public static IntStream range(int startInclusive, int endExclusive) {
        int length = endExclusive > startInclusive ? endExclusive - startInclusive : 0;
        int[] out = new int[length];
        for (int i = 0; i < length; i++) {
            out[i] = startInclusive + i;
        }
        return new IntStream(out);
    }

    public static IntStream rangeClosed(int startInclusive, int endInclusive) {
        int length = endInclusive >= startInclusive ? endInclusive - startInclusive + 1 : 0;
        int[] out = new int[length];
        for (int i = 0; i < length; i++) {
            out[i] = startInclusive + i;
        }
        return new IntStream(out);
    }

    public static IntStream of(int... values) {
        if (values == null) {
            return new IntStream(new int[0]);
        }
        return new IntStream(values);
    }

    public int sum() {
        int total = 0;
        for (int i = 0; i < data.length; i++) {
            total += data[i];
        }
        return total;
    }

    public long count() {
        return data.length;
    }

    public IntStream map(IntUnaryOperator mapper) {
        int[] out = new int[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = mapper.applyAsInt(data[i]);
        }
        return new IntStream(out);
    }

    public IntStream filter(IntPredicate predicate) {
        int[] tmp = new int[data.length];
        int n = 0;
        for (int i = 0; i < data.length; i++) {
            if (predicate.test(data[i])) {
                tmp[n++] = data[i];
            }
        }
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = tmp[i];
        }
        return new IntStream(out);
    }

    public int reduce(int identity, IntBinaryOperator op) {
        int result = identity;
        for (int i = 0; i < data.length; i++) {
            result = op.applyAsInt(result, data[i]);
        }
        return result;
    }

    public OptionalInt min() {
        if (data.length == 0) {
            return OptionalInt.empty();
        }
        int m = data[0];
        for (int i = 1; i < data.length; i++) {
            if (data[i] < m) {
                m = data[i];
            }
        }
        return OptionalInt.of(m);
    }

    public OptionalInt max() {
        if (data.length == 0) {
            return OptionalInt.empty();
        }
        int m = data[0];
        for (int i = 1; i < data.length; i++) {
            if (data[i] > m) {
                m = data[i];
            }
        }
        return OptionalInt.of(m);
    }

    public OptionalDouble average() {
        if (data.length == 0) {
            return OptionalDouble.empty();
        }
        long total = 0;
        for (int i = 0; i < data.length; i++) {
            total += data[i];
        }
        return OptionalDouble.of((double) total / (double) data.length);
    }

    public IntStream distinct() {
        int[] tmp = new int[data.length];
        int n = 0;
        for (int i = 0; i < data.length; i++) {
            boolean seen = false;
            for (int j = 0; j < n; j++) {
                if (tmp[j] == data[i]) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                tmp[n++] = data[i];
            }
        }
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = tmp[i];
        }
        return new IntStream(out);
    }

    public IntStream sorted() {
        int[] out = new int[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = data[i];
        }
        for (int i = 1; i < out.length; i++) {
            int key = out[i];
            int j = i - 1;
            while (j >= 0 && out[j] > key) {
                out[j + 1] = out[j];
                j--;
            }
            out[j + 1] = key;
        }
        return new IntStream(out);
    }

    public IntStream limit(long maxSize) {
        if (maxSize < 0) {
            throw new IllegalArgumentException(Long.toString(maxSize));
        }
        int length = data.length;
        if (maxSize < length) {
            length = (int) maxSize;
        }
        int[] out = new int[length];
        for (int i = 0; i < length; i++) {
            out[i] = data[i];
        }
        return new IntStream(out);
    }

    public IntStream skip(long n) {
        if (n < 0) {
            throw new IllegalArgumentException(Long.toString(n));
        }
        int start = data.length;
        if (n < start) {
            start = (int) n;
        }
        int length = data.length - start;
        int[] out = new int[length];
        for (int i = 0; i < length; i++) {
            out[i] = data[start + i];
        }
        return new IntStream(out);
    }

    public Stream mapToObj(IntFunction mapper) {
        ArrayList list = new ArrayList();
        for (int i = 0; i < data.length; i++) {
            list.add(mapper.apply(data[i]));
        }
        return new Stream(list);
    }

    public LongStream mapToLong(IntToLongFunction mapper) {
        long[] out = new long[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = mapper.applyAsLong(data[i]);
        }
        return LongStream.of(out);
    }

    public DoubleStream mapToDouble(IntToDoubleFunction mapper) {
        double[] out = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = mapper.applyAsDouble(data[i]);
        }
        return DoubleStream.of(out);
    }

    public LongStream asLongStream() {
        long[] out = new long[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = data[i];
        }
        return LongStream.of(out);
    }

    public DoubleStream asDoubleStream() {
        double[] out = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = data[i];
        }
        return DoubleStream.of(out);
    }

    public IntStream flatMap(IntFunction mapper) {
        ArrayList parts = new ArrayList();
        int total = 0;
        for (int i = 0; i < data.length; i++) {
            IntStream sub = (IntStream) mapper.apply(data[i]);
            int[] arr = sub.toArray();
            parts.add(arr);
            total += arr.length;
        }
        int[] out = new int[total];
        int n = 0;
        for (int p = 0; p < parts.size(); p++) {
            int[] arr = (int[]) parts.get(p);
            for (int i = 0; i < arr.length; i++) {
                out[n++] = arr[i];
            }
        }
        return new IntStream(out);
    }

    /** Boxes each int into an Integer and returns an object Stream. */
    public Stream boxed() {
        ArrayList list = new ArrayList();
        for (int i = 0; i < data.length; i++) {
            list.add(Integer.valueOf(data[i]));
        }
        return new Stream(list);
    }

    public boolean anyMatch(IntPredicate predicate) {
        for (int i = 0; i < data.length; i++) {
            if (predicate.test(data[i])) {
                return true;
            }
        }
        return false;
    }

    public boolean allMatch(IntPredicate predicate) {
        for (int i = 0; i < data.length; i++) {
            if (!predicate.test(data[i])) {
                return false;
            }
        }
        return true;
    }

    public boolean noneMatch(IntPredicate predicate) {
        for (int i = 0; i < data.length; i++) {
            if (predicate.test(data[i])) {
                return false;
            }
        }
        return true;
    }

    public OptionalInt findFirst() {
        if (data.length == 0) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(data[0]);
    }

    public OptionalInt findAny() {
        return findFirst();
    }

    public IntStream peek(IntConsumer action) {
        for (int i = 0; i < data.length; i++) {
            action.accept(data[i]);
        }
        return new IntStream(data);
    }

    public void forEach(IntConsumer action) {
        for (int i = 0; i < data.length; i++) {
            action.accept(data[i]);
        }
    }

    public void forEachOrdered(IntConsumer action) {
        for (int i = 0; i < data.length; i++) {
            action.accept(data[i]);
        }
    }

    public int[] toArray() {
        int[] out = new int[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = data[i];
        }
        return out;
    }
}
