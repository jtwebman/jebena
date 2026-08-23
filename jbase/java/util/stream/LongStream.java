package java.util.stream;

import java.util.ArrayList;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.function.LongBinaryOperator;
import java.util.function.LongPredicate;
import java.util.function.LongUnaryOperator;

/**
 * Clean-room, EAGER primitive long stream backed by a {@code long[]}. Every
 * intermediate operation fully realizes its result into a fresh {@code long[]}
 * and returns a new {@code LongStream}.
 */
public class LongStream {

    private final long[] data;

    private LongStream(long[] values) {
        this.data = new long[values.length];
        for (int i = 0; i < values.length; i++) {
            this.data[i] = values[i];
        }
    }

    public static LongStream range(long startInclusive, long endExclusive) {
        long lengthLong = endExclusive > startInclusive ? endExclusive - startInclusive : 0L;
        int length = (int) lengthLong;
        long[] out = new long[length];
        for (int i = 0; i < length; i++) {
            out[i] = startInclusive + i;
        }
        return new LongStream(out);
    }

    public static LongStream rangeClosed(long startInclusive, long endInclusive) {
        long lengthLong = endInclusive >= startInclusive ? endInclusive - startInclusive + 1L : 0L;
        int length = (int) lengthLong;
        long[] out = new long[length];
        for (int i = 0; i < length; i++) {
            out[i] = startInclusive + i;
        }
        return new LongStream(out);
    }

    public static LongStream of(long... values) {
        if (values == null) {
            return new LongStream(new long[0]);
        }
        return new LongStream(values);
    }

    public long sum() {
        long total = 0L;
        for (int i = 0; i < data.length; i++) {
            total += data[i];
        }
        return total;
    }

    public long count() {
        return data.length;
    }

    public LongStream map(LongUnaryOperator mapper) {
        long[] out = new long[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = mapper.applyAsLong(data[i]);
        }
        return new LongStream(out);
    }

    public LongStream filter(LongPredicate predicate) {
        long[] tmp = new long[data.length];
        int n = 0;
        for (int i = 0; i < data.length; i++) {
            if (predicate.test(data[i])) {
                tmp[n++] = data[i];
            }
        }
        long[] out = new long[n];
        for (int i = 0; i < n; i++) {
            out[i] = tmp[i];
        }
        return new LongStream(out);
    }

    public long reduce(long identity, LongBinaryOperator op) {
        long result = identity;
        for (int i = 0; i < data.length; i++) {
            result = op.applyAsLong(result, data[i]);
        }
        return result;
    }

    public OptionalLong min() {
        if (data.length == 0) {
            return OptionalLong.empty();
        }
        long m = data[0];
        for (int i = 1; i < data.length; i++) {
            if (data[i] < m) {
                m = data[i];
            }
        }
        return OptionalLong.of(m);
    }

    public OptionalLong max() {
        if (data.length == 0) {
            return OptionalLong.empty();
        }
        long m = data[0];
        for (int i = 1; i < data.length; i++) {
            if (data[i] > m) {
                m = data[i];
            }
        }
        return OptionalLong.of(m);
    }

    public OptionalDouble average() {
        if (data.length == 0) {
            return OptionalDouble.empty();
        }
        double total = 0.0;
        for (int i = 0; i < data.length; i++) {
            total += (double) data[i];
        }
        return OptionalDouble.of(total / (double) data.length);
    }

    /** Boxes each long into a Long and returns an object Stream. */
    public Stream boxed() {
        ArrayList list = new ArrayList();
        for (int i = 0; i < data.length; i++) {
            list.add(Long.valueOf(data[i]));
        }
        return new Stream(list);
    }

    public long[] toArray() {
        long[] out = new long[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = data[i];
        }
        return out;
    }
}
