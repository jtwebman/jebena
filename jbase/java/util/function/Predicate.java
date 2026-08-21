package java.util.function;

import java.util.Objects;

public interface Predicate<T> {
    boolean test(T t);

    default Predicate<T> and(Predicate<? super T> other) {
        Objects.requireNonNull(other);
        return (T t) -> test(t) && other.test(t);
    }

    default Predicate<T> or(Predicate<? super T> other) {
        Objects.requireNonNull(other);
        return (T t) -> test(t) || other.test(t);
    }

    default Predicate<T> negate() {
        return (T t) -> !test(t);
    }
}
