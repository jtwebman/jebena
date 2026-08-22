package java.util.concurrent;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Clean-room java.util.concurrent.ConcurrentMap. Jebena is currently
 * single-threaded, so this carries no memory-model guarantees; it exists only to
 * give ConcurrentHashMap the same erased method surface real OpenJDK exposes.
 *
 * <p>The jbase {@code java.util.Map} is a minimal, raw (non-generic) interface and
 * declares none of the Java 8+ default methods, so ConcurrentMap re-declares the
 * atomic/functional operations here (as raw {@code Object} signatures, which match
 * the erased descriptors a generic driver compiled against the real JDK emits).
 */
public interface ConcurrentMap extends Map {

    Object getOrDefault(Object key, Object defaultValue);

    void forEach(BiConsumer action);

    Object putIfAbsent(Object key, Object value);

    boolean remove(Object key, Object value);

    boolean replace(Object key, Object oldValue, Object newValue);

    Object replace(Object key, Object value);

    Object computeIfAbsent(Object key, Function mappingFunction);

    Object computeIfPresent(Object key, BiFunction remappingFunction);

    Object compute(Object key, BiFunction remappingFunction);

    Object merge(Object key, Object value, BiFunction remappingFunction);
}
