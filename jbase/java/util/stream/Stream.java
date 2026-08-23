package java.util.stream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.function.ToDoubleFunction;
import java.util.function.UnaryOperator;

/**
 * Clean-room, EAGER java.util.stream.Stream. Every intermediate operation
 * fully realizes its result into a fresh {@link ArrayList} and returns a new
 * {@code Stream} backed by that list. Declared raw on purpose; our functional
 * interfaces are used without type arguments.
 */
public class Stream {

    private final ArrayList data;

    /** Copies the given backing list into a private ArrayList. */
    public Stream(List backing) {
        this.data = copyList(backing);
    }

    /** Builds a fresh ArrayList holding the same elements as {@code src}. */
    private static ArrayList copyList(List src) {
        ArrayList out = new ArrayList();
        for (int i = 0; i < src.size(); i++) {
            out.add(src.get(i));
        }
        return out;
    }

    /** Wraps the values into a new Stream. */
    public static Stream of(Object... values) {
        ArrayList list = new ArrayList();
        if (values != null) {
            for (int i = 0; i < values.length; i++) {
                list.add(values[i]);
            }
        }
        return new Stream(list);
    }

    /** Wraps an existing list (copied) into a new Stream. */
    public static Stream ofList(List list) {
        return new Stream(list);
    }

    /**
     * Finite iterate: starting from {@code seed}, while {@code hasNext} holds,
     * emit the current value and advance with {@code next}. Realized eagerly.
     */
    public static Stream iterate(Object seed, Predicate hasNext, UnaryOperator next) {
        ArrayList out = new ArrayList();
        Object cur = seed;
        while (hasNext.test(cur)) {
            out.add(cur);
            cur = next.apply(cur);
        }
        return new Stream(out);
    }

    // ---- Intermediate operations (return a new Stream) ----

    public Stream filter(Predicate predicate) {
        ArrayList out = new ArrayList();
        for (int i = 0; i < data.size(); i++) {
            Object o = data.get(i);
            if (predicate.test(o)) {
                out.add(o);
            }
        }
        return new Stream(out);
    }

    public Stream map(Function mapper) {
        ArrayList out = new ArrayList();
        for (int i = 0; i < data.size(); i++) {
            out.add(mapper.apply(data.get(i)));
        }
        return new Stream(out);
    }

    public Stream distinct() {
        HashSet seen = new HashSet();
        ArrayList out = new ArrayList();
        for (int i = 0; i < data.size(); i++) {
            Object o = data.get(i);
            if (seen.add(o)) {
                out.add(o);
            }
        }
        return new Stream(out);
    }

    public Stream sorted() {
        ArrayList out = copyList(data);
        Collections.sort(out);
        return new Stream(out);
    }

    public Stream sorted(Comparator comparator) {
        ArrayList out = copyList(data);
        Collections.sort(out, comparator);
        return new Stream(out);
    }

    public Stream limit(long maxSize) {
        ArrayList out = new ArrayList();
        long limit = maxSize < 0 ? 0 : maxSize;
        for (int i = 0; i < data.size() && i < limit; i++) {
            out.add(data.get(i));
        }
        return new Stream(out);
    }

    public Stream skip(long n) {
        ArrayList out = new ArrayList();
        long skip = n < 0 ? 0 : n;
        for (int i = 0; i < data.size(); i++) {
            if (i >= skip) {
                out.add(data.get(i));
            }
        }
        return new Stream(out);
    }

    public Stream peek(Consumer action) {
        ArrayList out = new ArrayList();
        for (int i = 0; i < data.size(); i++) {
            Object o = data.get(i);
            action.accept(o);
            out.add(o);
        }
        return new Stream(out);
    }

    /**
     * Maps each element to a Stream and flattens the results into one Stream.
     * A null mapping result contributes no elements.
     */
    public Stream flatMap(Function mapper) {
        ArrayList out = new ArrayList();
        for (int i = 0; i < data.size(); i++) {
            Object mapped = mapper.apply(data.get(i));
            if (mapped != null) {
                Stream sub = (Stream) mapped;
                for (int j = 0; j < sub.data.size(); j++) {
                    out.add(sub.data.get(j));
                }
            }
        }
        return new Stream(out);
    }

    /** Elements from the front while the predicate holds; stops at first failure. */
    public Stream takeWhile(Predicate predicate) {
        ArrayList out = new ArrayList();
        for (int i = 0; i < data.size(); i++) {
            Object o = data.get(i);
            if (!predicate.test(o)) {
                break;
            }
            out.add(o);
        }
        return new Stream(out);
    }

    /** Drops elements from the front while the predicate holds; keeps the rest. */
    public Stream dropWhile(Predicate predicate) {
        ArrayList out = new ArrayList();
        boolean dropping = true;
        for (int i = 0; i < data.size(); i++) {
            Object o = data.get(i);
            if (dropping && predicate.test(o)) {
                continue;
            }
            dropping = false;
            out.add(o);
        }
        return new Stream(out);
    }

    /** Concatenates the elements of {@code a} followed by those of {@code b}. */
    public static Stream concat(Stream a, Stream b) {
        ArrayList out = new ArrayList();
        for (int i = 0; i < a.data.size(); i++) {
            out.add(a.data.get(i));
        }
        for (int i = 0; i < b.data.size(); i++) {
            out.add(b.data.get(i));
        }
        return new Stream(out);
    }

    // ---- Terminal operations ----

    public void forEach(Consumer action) {
        for (int i = 0; i < data.size(); i++) {
            action.accept(data.get(i));
        }
    }

    public long count() {
        return data.size();
    }

    public Object reduce(Object identity, BinaryOperator accumulator) {
        Object result = identity;
        for (int i = 0; i < data.size(); i++) {
            result = accumulator.apply(result, data.get(i));
        }
        return result;
    }

    public Optional reduce(BinaryOperator accumulator) {
        if (data.isEmpty()) {
            return Optional.empty();
        }
        Object result = data.get(0);
        for (int i = 1; i < data.size(); i++) {
            result = accumulator.apply(result, data.get(i));
        }
        return Optional.of(result);
    }

    public Object collect(Collector collector) {
        return collector.collect(copyList(data));
    }

    public List toList() {
        return copyList(data);
    }

    public Object[] toArray() {
        return data.toArray();
    }

    public boolean anyMatch(Predicate predicate) {
        for (int i = 0; i < data.size(); i++) {
            if (predicate.test(data.get(i))) {
                return true;
            }
        }
        return false;
    }

    public boolean allMatch(Predicate predicate) {
        for (int i = 0; i < data.size(); i++) {
            if (!predicate.test(data.get(i))) {
                return false;
            }
        }
        return true;
    }

    public boolean noneMatch(Predicate predicate) {
        for (int i = 0; i < data.size(); i++) {
            if (predicate.test(data.get(i))) {
                return false;
            }
        }
        return true;
    }

    public Optional min(Comparator comparator) {
        if (data.isEmpty()) {
            return Optional.empty();
        }
        Object result = data.get(0);
        for (int i = 1; i < data.size(); i++) {
            Object o = data.get(i);
            if (comparator.compare(o, result) < 0) {
                result = o;
            }
        }
        return Optional.of(result);
    }

    public Optional max(Comparator comparator) {
        if (data.isEmpty()) {
            return Optional.empty();
        }
        Object result = data.get(0);
        for (int i = 1; i < data.size(); i++) {
            Object o = data.get(i);
            if (comparator.compare(o, result) > 0) {
                result = o;
            }
        }
        return Optional.of(result);
    }

    public Optional findFirst() {
        if (data.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(data.get(0));
    }

    public IntStream mapToInt(ToIntFunction mapper) {
        int[] out = new int[data.size()];
        for (int i = 0; i < data.size(); i++) {
            out[i] = mapper.applyAsInt(data.get(i));
        }
        return IntStream.of(out);
    }

    public LongStream mapToLong(ToLongFunction mapper) {
        long[] out = new long[data.size()];
        for (int i = 0; i < data.size(); i++) {
            out[i] = mapper.applyAsLong(data.get(i));
        }
        return LongStream.of(out);
    }

    public DoubleStream mapToDouble(ToDoubleFunction mapper) {
        double[] out = new double[data.size()];
        for (int i = 0; i < data.size(); i++) {
            out[i] = mapper.applyAsDouble(data.get(i));
        }
        return DoubleStream.of(out);
    }
}
