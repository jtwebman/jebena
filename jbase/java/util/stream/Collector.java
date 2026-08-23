package java.util.stream;

import java.util.ArrayList;
import java.util.function.Supplier;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;

/**
 * A simplified, single-method collector. Given the stream's fully-realized
 * element list, produce the collected result. This is intentionally NOT the
 * real three-function {@code Collector}; a single reduce-the-whole-list method
 * is enough for our eager pipeline.
 */
public interface Collector {
    Object collect(ArrayList data);

    /**
     * Build a Collector from the classic supplier/accumulator/combiner/finisher
     * quartet. Because our pipeline is eager and single-threaded there is only
     * ever one accumulation container, so the {@code combiner} is accepted for
     * API compatibility but never invoked; the {@code characteristics} are
     * likewise accepted (matching the JDK signature so real-javac call sites
     * link) and ignored.
     */
    static Collector of(Supplier supplier, BiConsumer accumulator,
                        BinaryOperator combiner, Function finisher,
                        Characteristics... characteristics) {
        return new CollectorImpl(supplier, accumulator, finisher);
    }

    /**
     * Build a Collector with an identity finisher: the accumulation container
     * itself is the result. The {@code combiner} and {@code characteristics}
     * are unused in the eager single-threaded model.
     */
    static Collector of(Supplier supplier, BiConsumer accumulator,
                        BinaryOperator combiner,
                        Characteristics... characteristics) {
        return new CollectorImpl(supplier, accumulator, null);
    }

    /**
     * Collector characteristics. Real javac desugars the varargs {@code of}
     * calls into an {@code anewarray} of this type, so it must exist and be
     * loadable even though our eager pipeline never consults it. Hand-rolled as
     * an enum-like final class (the {@code enum} keyword does not compile
     * against our non-generic {@link Enum}).
     */
    public static final class Characteristics {
        public static final Characteristics CONCURRENT =
                new Characteristics("CONCURRENT", 0);
        public static final Characteristics UNORDERED =
                new Characteristics("UNORDERED", 1);
        public static final Characteristics IDENTITY_FINISH =
                new Characteristics("IDENTITY_FINISH", 2);

        private final String name;
        private final int ordinal;

        private Characteristics(String name, int ordinal) {
            this.name = name;
            this.ordinal = ordinal;
        }

        public String name() {
            return name;
        }

        public int ordinal() {
            return ordinal;
        }

        public String toString() {
            return name;
        }
    }
}
