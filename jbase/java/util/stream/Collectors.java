package java.util.stream;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IntSummaryStatistics;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

/**
 * Clean-room factories for {@link Collector}. Each returned collector consumes
 * the stream's realized element list and produces the final result.
 */
public class Collectors {

    private Collectors() {
    }

    public static Collector toList() {
        return new Collector() {
            public Object collect(ArrayList data) {
                ArrayList out = new ArrayList();
                for (int i = 0; i < data.size(); i++) {
                    out.add(data.get(i));
                }
                return out;
            }
        };
    }

    public static Collector toSet() {
        return new Collector() {
            public Object collect(ArrayList data) {
                HashSet out = new HashSet();
                for (int i = 0; i < data.size(); i++) {
                    out.add(data.get(i));
                }
                return out;
            }
        };
    }

    public static Collector joining() {
        return joining("", "", "");
    }

    public static Collector joining(CharSequence delimiter) {
        return joining(delimiter, "", "");
    }

    public static Collector joining(final CharSequence delimiter,
                                    final CharSequence prefix,
                                    final CharSequence suffix) {
        return new Collector() {
            public Object collect(ArrayList data) {
                StringBuilder sb = new StringBuilder();
                sb.append(prefix);
                for (int i = 0; i < data.size(); i++) {
                    if (i > 0) {
                        sb.append(delimiter);
                    }
                    sb.append(String.valueOf(data.get(i)));
                }
                sb.append(suffix);
                return sb.toString();
            }
        };
    }

    public static Collector counting() {
        return new Collector() {
            public Object collect(ArrayList data) {
                return Long.valueOf(data.size());
            }
        };
    }

    public static Collector summingInt(final ToIntFunction mapper) {
        return new Collector() {
            public Object collect(ArrayList data) {
                int sum = 0;
                for (int i = 0; i < data.size(); i++) {
                    sum += mapper.applyAsInt(data.get(i));
                }
                return Integer.valueOf(sum);
            }
        };
    }

    public static Collector summingLong(final ToLongFunction mapper) {
        return new Collector() {
            public Object collect(ArrayList data) {
                long sum = 0L;
                for (int i = 0; i < data.size(); i++) {
                    sum += mapper.applyAsLong(data.get(i));
                }
                return Long.valueOf(sum);
            }
        };
    }

    public static Collector averagingInt(final ToIntFunction mapper) {
        return new Collector() {
            public Object collect(ArrayList data) {
                double sum = 0.0d;
                long count = 0L;
                for (int i = 0; i < data.size(); i++) {
                    sum += mapper.applyAsInt(data.get(i));
                    count++;
                }
                return Double.valueOf(count == 0 ? 0.0d : sum / count);
            }
        };
    }

    public static Collector averagingLong(final ToLongFunction mapper) {
        return new Collector() {
            public Object collect(ArrayList data) {
                double sum = 0.0d;
                long count = 0L;
                for (int i = 0; i < data.size(); i++) {
                    sum += mapper.applyAsLong(data.get(i));
                    count++;
                }
                return Double.valueOf(count == 0 ? 0.0d : sum / count);
            }
        };
    }

    public static Collector toMap(final Function keyMapper, final Function valueMapper) {
        return new Collector() {
            public Object collect(ArrayList data) {
                HashMap map = new HashMap();
                for (int i = 0; i < data.size(); i++) {
                    Object element = data.get(i);
                    Object key = keyMapper.apply(element);
                    Object value = valueMapper.apply(element);
                    if (map.containsKey(key)) {
                        throw new IllegalStateException(
                                "Duplicate key " + key
                                        + " (attempted merging values " + map.get(key)
                                        + " and " + value + ")");
                    }
                    map.put(key, value);
                }
                return map;
            }
        };
    }

    public static Collector toMap(final Function keyMapper, final Function valueMapper,
                                  final BinaryOperator mergeFunction) {
        return new Collector() {
            public Object collect(ArrayList data) {
                HashMap map = new HashMap();
                for (int i = 0; i < data.size(); i++) {
                    Object element = data.get(i);
                    Object key = keyMapper.apply(element);
                    Object value = valueMapper.apply(element);
                    if (map.containsKey(key)) {
                        map.put(key, mergeFunction.apply(map.get(key), value));
                    } else {
                        map.put(key, value);
                    }
                }
                return map;
            }
        };
    }

    public static Collector minBy(final Comparator comparator) {
        return new Collector() {
            public Object collect(ArrayList data) {
                if (data.size() == 0) {
                    return Optional.empty();
                }
                Object min = data.get(0);
                for (int i = 1; i < data.size(); i++) {
                    Object element = data.get(i);
                    if (comparator.compare(element, min) < 0) {
                        min = element;
                    }
                }
                return Optional.of(min);
            }
        };
    }

    public static Collector maxBy(final Comparator comparator) {
        return new Collector() {
            public Object collect(ArrayList data) {
                if (data.size() == 0) {
                    return Optional.empty();
                }
                Object max = data.get(0);
                for (int i = 1; i < data.size(); i++) {
                    Object element = data.get(i);
                    if (comparator.compare(element, max) > 0) {
                        max = element;
                    }
                }
                return Optional.of(max);
            }
        };
    }

    public static Collector teeing(final Collector downstream1, final Collector downstream2,
                                   final BiFunction merger) {
        return new Collector() {
            public Object collect(ArrayList data) {
                Object r1 = downstream1.collect(data);
                Object r2 = downstream2.collect(data);
                return merger.apply(r1, r2);
            }
        };
    }

    public static Collector mapping(final Function mapper, final Collector downstream) {
        return new Collector() {
            public Object collect(ArrayList data) {
                ArrayList mapped = new ArrayList();
                for (int i = 0; i < data.size(); i++) {
                    mapped.add(mapper.apply(data.get(i)));
                }
                return downstream.collect(mapped);
            }
        };
    }

    public static Collector partitioningBy(final Predicate predicate) {
        return new Collector() {
            public Object collect(ArrayList data) {
                ArrayList falses = new ArrayList();
                ArrayList trues = new ArrayList();
                for (int i = 0; i < data.size(); i++) {
                    Object element = data.get(i);
                    if (predicate.test(element)) {
                        trues.add(element);
                    } else {
                        falses.add(element);
                    }
                }
                // JDK's Partition is a Map with exactly {false=..., true=...}.
                HashMap map = new HashMap();
                map.put(Boolean.FALSE, falses);
                map.put(Boolean.TRUE, trues);
                return map;
            }
        };
    }

    public static Collector groupingBy(final Function classifier) {
        return new Collector() {
            public Object collect(ArrayList data) {
                LinkedHashMap map = new LinkedHashMap();
                for (int i = 0; i < data.size(); i++) {
                    Object element = data.get(i);
                    Object key = classifier.apply(element);
                    Object bucket = map.get(key);
                    if (bucket == null) {
                        bucket = new ArrayList();
                        map.put(key, bucket);
                    }
                    ((ArrayList) bucket).add(element);
                }
                return map;
            }
        };
    }

    public static Collector groupingBy(final Function classifier, final Collector downstream) {
        return new Collector() {
            public Object collect(ArrayList data) {
                LinkedHashMap groups = new LinkedHashMap();
                for (int i = 0; i < data.size(); i++) {
                    Object element = data.get(i);
                    Object key = classifier.apply(element);
                    Object bucket = groups.get(key);
                    if (bucket == null) {
                        bucket = new ArrayList();
                        groups.put(key, bucket);
                    }
                    ((ArrayList) bucket).add(element);
                }
                LinkedHashMap result = new LinkedHashMap();
                Iterator it = groups.keySet().iterator();
                while (it.hasNext()) {
                    Object key = it.next();
                    ArrayList bucket = (ArrayList) groups.get(key);
                    result.put(key, downstream.collect(bucket));
                }
                return result;
            }
        };
    }

    public static Collector groupingBy(final Function classifier, final Supplier mapFactory,
                                       final Collector downstream) {
        return new Collector() {
            public Object collect(ArrayList data) {
                LinkedHashMap groups = new LinkedHashMap();
                for (int i = 0; i < data.size(); i++) {
                    Object element = data.get(i);
                    Object key = classifier.apply(element);
                    Object bucket = groups.get(key);
                    if (bucket == null) {
                        bucket = new ArrayList();
                        groups.put(key, bucket);
                    }
                    ((ArrayList) bucket).add(element);
                }
                // Realize each bucket's downstream result up front, then copy
                // into the caller's map. The map is narrowed to a DIRECTLY
                // implemented interface (the VM's checkcast to a transitive
                // super-interface such as Map-of-TreeMap is unreliable), so a
                // TreeMap is filled through NavigableMap and a ConcurrentHashMap
                // through ConcurrentMap, both of which inherit put from Map.
                ArrayList keys = new ArrayList();
                ArrayList vals = new ArrayList();
                Iterator it = groups.keySet().iterator();
                while (it.hasNext()) {
                    Object key = it.next();
                    ArrayList bucket = (ArrayList) groups.get(key);
                    keys.add(key);
                    vals.add(downstream.collect(bucket));
                }
                Object mapObj = mapFactory.get();
                if (mapObj instanceof NavigableMap) {
                    NavigableMap result = (NavigableMap) mapObj;
                    for (int i = 0; i < keys.size(); i++) {
                        result.put(keys.get(i), vals.get(i));
                    }
                    return result;
                }
                if (mapObj instanceof ConcurrentMap) {
                    ConcurrentMap result = (ConcurrentMap) mapObj;
                    for (int i = 0; i < keys.size(); i++) {
                        result.put(keys.get(i), vals.get(i));
                    }
                    return result;
                }
                Map result = (Map) mapObj;
                for (int i = 0; i < keys.size(); i++) {
                    result.put(keys.get(i), vals.get(i));
                }
                return result;
            }
        };
    }

    public static Collector groupingByConcurrent(final Function classifier) {
        return new Collector() {
            public Object collect(ArrayList data) {
                ConcurrentHashMap map = new ConcurrentHashMap();
                for (int i = 0; i < data.size(); i++) {
                    Object element = data.get(i);
                    Object key = classifier.apply(element);
                    Object bucket = map.get(key);
                    if (bucket == null) {
                        bucket = new ArrayList();
                        map.put(key, bucket);
                    }
                    ((ArrayList) bucket).add(element);
                }
                return map;
            }
        };
    }

    public static Collector groupingByConcurrent(final Function classifier, final Collector downstream) {
        return new Collector() {
            public Object collect(ArrayList data) {
                LinkedHashMap groups = new LinkedHashMap();
                for (int i = 0; i < data.size(); i++) {
                    Object element = data.get(i);
                    Object key = classifier.apply(element);
                    Object bucket = groups.get(key);
                    if (bucket == null) {
                        bucket = new ArrayList();
                        groups.put(key, bucket);
                    }
                    ((ArrayList) bucket).add(element);
                }
                ConcurrentHashMap result = new ConcurrentHashMap();
                Iterator it = groups.keySet().iterator();
                while (it.hasNext()) {
                    Object key = it.next();
                    ArrayList bucket = (ArrayList) groups.get(key);
                    result.put(key, downstream.collect(bucket));
                }
                return result;
            }
        };
    }

    public static Collector partitioningBy(final Predicate predicate, final Collector downstream) {
        return new Collector() {
            public Object collect(ArrayList data) {
                ArrayList falses = new ArrayList();
                ArrayList trues = new ArrayList();
                for (int i = 0; i < data.size(); i++) {
                    Object element = data.get(i);
                    if (predicate.test(element)) {
                        trues.add(element);
                    } else {
                        falses.add(element);
                    }
                }
                HashMap map = new HashMap();
                map.put(Boolean.FALSE, downstream.collect(falses));
                map.put(Boolean.TRUE, downstream.collect(trues));
                return map;
            }
        };
    }

    public static Collector toUnmodifiableList() {
        return new Collector() {
            public Object collect(ArrayList data) {
                ArrayList out = new ArrayList();
                for (int i = 0; i < data.size(); i++) {
                    out.add(data.get(i));
                }
                return out;
            }
        };
    }

    public static Collector reducing(final Object identity, final BinaryOperator op) {
        return new Collector() {
            public Object collect(ArrayList data) {
                Object result = identity;
                for (int i = 0; i < data.size(); i++) {
                    result = op.apply(result, data.get(i));
                }
                return result;
            }
        };
    }

    public static Collector collectingAndThen(final Collector downstream, final Function finisher) {
        return new Collector() {
            public Object collect(ArrayList data) {
                return finisher.apply(downstream.collect(data));
            }
        };
    }

    public static Collector filtering(final Predicate predicate, final Collector downstream) {
        return new Collector() {
            public Object collect(ArrayList data) {
                ArrayList filtered = new ArrayList();
                for (int i = 0; i < data.size(); i++) {
                    Object element = data.get(i);
                    if (predicate.test(element)) {
                        filtered.add(element);
                    }
                }
                return downstream.collect(filtered);
            }
        };
    }

    public static Collector flatMapping(final Function mapperToStream, final Collector downstream) {
        return new Collector() {
            public Object collect(ArrayList data) {
                ArrayList flattened = new ArrayList();
                for (int i = 0; i < data.size(); i++) {
                    Object mapped = mapperToStream.apply(data.get(i));
                    if (mapped != null) {
                        Stream sub = (Stream) mapped;
                        Iterator it = sub.toList().iterator();
                        while (it.hasNext()) {
                            flattened.add(it.next());
                        }
                    }
                }
                return downstream.collect(flattened);
            }
        };
    }

    public static Collector toCollection(final Supplier collectionFactory) {
        return new Collector() {
            public Object collect(ArrayList data) {
                Object out = collectionFactory.get();
                // The VM's checkcast to Collection (a transitive super-interface)
                // is unreliable, so narrow to the directly-implemented interface.
                if (out instanceof List) {
                    List list = (List) out;
                    for (int i = 0; i < data.size(); i++) {
                        list.add(data.get(i));
                    }
                } else if (out instanceof Set) {
                    Set set = (Set) out;
                    for (int i = 0; i < data.size(); i++) {
                        set.add(data.get(i));
                    }
                }
                return out;
            }
        };
    }

    public static Collector summarizingInt(final ToIntFunction mapper) {
        return new Collector() {
            public Object collect(ArrayList data) {
                IntSummaryStatistics stats = new IntSummaryStatistics();
                for (int i = 0; i < data.size(); i++) {
                    stats.accept(mapper.applyAsInt(data.get(i)));
                }
                return stats;
            }
        };
    }
}
