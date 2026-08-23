package java.util;

import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.function.ToDoubleFunction;
import java.util.function.Function;

public interface Comparator<T> {
    int compare(T a, T b);

    default Comparator<T> reversed() {
        final Comparator self = this;
        return (Comparator<T>) new Comparator() {
            public int compare(Object a, Object b) {
                return self.compare(b, a);
            }
        };
    }

    default Comparator<T> thenComparing(Comparator other) {
        final Comparator self = this;
        final Comparator o = other;
        return (Comparator<T>) new Comparator() {
            public int compare(Object a, Object b) {
                int r = self.compare(a, b);
                if (r != 0) {
                    return r;
                }
                return o.compare(a, b);
            }
        };
    }

    default Comparator<T> thenComparingInt(ToIntFunction keyExtractor) {
        return thenComparing(comparingInt(keyExtractor));
    }

    static Comparator naturalOrder() {
        return new Comparator() {
            public int compare(Object a, Object b) {
                return ((Comparable) a).compareTo(b);
            }
        };
    }

    static Comparator reverseOrder() {
        return new Comparator() {
            public int compare(Object a, Object b) {
                return ((Comparable) b).compareTo(a);
            }
        };
    }

    static Comparator comparingInt(ToIntFunction keyExtractor) {
        final ToIntFunction ke = keyExtractor;
        return new Comparator() {
            public int compare(Object a, Object b) {
                int ka = ke.applyAsInt(a);
                int kb = ke.applyAsInt(b);
                return (ka < kb) ? -1 : ((ka == kb) ? 0 : 1);
            }
        };
    }

    static Comparator comparingLong(ToLongFunction keyExtractor) {
        final ToLongFunction ke = keyExtractor;
        return new Comparator() {
            public int compare(Object a, Object b) {
                long ka = ke.applyAsLong(a);
                long kb = ke.applyAsLong(b);
                return (ka < kb) ? -1 : ((ka == kb) ? 0 : 1);
            }
        };
    }

    static Comparator comparingDouble(ToDoubleFunction keyExtractor) {
        final ToDoubleFunction ke = keyExtractor;
        return new Comparator() {
            public int compare(Object a, Object b) {
                double ka = ke.applyAsDouble(a);
                double kb = ke.applyAsDouble(b);
                return Double.compare(ka, kb);
            }
        };
    }

    static Comparator comparing(Function keyExtractor) {
        final Function ke = keyExtractor;
        return new Comparator() {
            public int compare(Object a, Object b) {
                Comparable ka = (Comparable) ke.apply(a);
                Object kb = ke.apply(b);
                return ka.compareTo(kb);
            }
        };
    }
}
