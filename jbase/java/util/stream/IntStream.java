package java.util.stream;

import java.util.ArrayList;
import java.util.function.IntUnaryOperator;

/**
 * Clean-room, EAGER primitive int stream backed by an {@code int[]}. Kept
 * deliberately minimal: no filter/anyMatch, because our jbase has no
 * {@code IntPredicate}.
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

    /** Boxes each int into an Integer and returns an object Stream. */
    public Stream boxed() {
        ArrayList list = new ArrayList();
        for (int i = 0; i < data.length; i++) {
            list.add(Integer.valueOf(data[i]));
        }
        return new Stream(list);
    }

    public int[] toArray() {
        int[] out = new int[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = data[i];
        }
        return out;
    }
}
