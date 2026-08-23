package java.util.stream;

import java.util.ArrayList;
import java.util.function.Supplier;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Backing implementation for {@link Collector#of}. Applies the supplier once to
 * create the accumulation container, feeds every element through the
 * accumulator, then (if a finisher is present) maps the container to the final
 * result. A {@code null} finisher means the identity finisher: the container is
 * itself the result.
 */
final class CollectorImpl implements Collector {
    private final Supplier supplier;
    private final BiConsumer accumulator;
    private final Function finisher;

    CollectorImpl(Supplier supplier, BiConsumer accumulator, Function finisher) {
        this.supplier = supplier;
        this.accumulator = accumulator;
        this.finisher = finisher;
    }

    public Object collect(ArrayList data) {
        Object a = supplier.get();
        for (int i = 0; i < data.size(); i++) {
            accumulator.accept(a, data.get(i));
        }
        if (finisher == null) {
            return a;
        }
        return finisher.apply(a);
    }
}
