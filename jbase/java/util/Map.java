package java.util;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public interface Map {
    int size();

    boolean isEmpty();

    Object get(Object key);

    Object put(Object key, Object value);

    Object remove(Object key);

    boolean containsKey(Object key);

    boolean containsValue(Object value);

    void clear();

    Set keySet();

    Collection values();

    Set entrySet();

    // JDK-style default methods, in terms of get/put/remove/containsKey/entrySet.
    // (ConcurrentHashMap overrides merge/compute/computeIfAbsent with synchronized
    // versions; other maps inherit these.)
    default Object getOrDefault(Object key, Object defaultValue) {
        Object v = get(key);
        return (v != null || containsKey(key)) ? v : defaultValue;
    }

    default Object putIfAbsent(Object key, Object value) {
        Object v = get(key);
        if (v == null) {
            v = put(key, value);
        }
        return v;
    }

    default Object merge(Object key, Object value, BiFunction remappingFunction) {
        Object old = get(key);
        Object nv = (old == null) ? value : remappingFunction.apply(old, value);
        if (nv == null) {
            remove(key);
        } else {
            put(key, nv);
        }
        return nv;
    }

    default Object compute(Object key, BiFunction remappingFunction) {
        Object old = get(key);
        Object nv = remappingFunction.apply(key, old);
        if (nv == null) {
            if (old != null || containsKey(key)) {
                remove(key);
            }
            return null;
        }
        put(key, nv);
        return nv;
    }

    default Object computeIfAbsent(Object key, Function mappingFunction) {
        Object v = get(key);
        if (v == null) {
            Object nv = mappingFunction.apply(key);
            if (nv != null) {
                put(key, nv);
                return nv;
            }
            return null;
        }
        return v;
    }

    default void forEach(BiConsumer action) {
        java.util.Iterator it = entrySet().iterator();
        while (it.hasNext()) {
            Entry en = (Entry) it.next();
            action.accept(en.getKey(), en.getValue());
        }
    }

    interface Entry {
        Object getKey();

        Object getValue();
    }
}
