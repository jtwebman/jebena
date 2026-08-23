import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DiffGbc {

    // groupingByConcurrent(classifier): sum of all bucket sizes plus bucket count.
    public static int gbcClassifierSizes() {
        Object r = Stream.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).collect(
                Collectors.groupingByConcurrent(x -> ((Integer) x) % 3));
        ConcurrentHashMap m = (ConcurrentHashMap) r;
        int sum = 0;
        java.util.Iterator it = m.values().iterator();
        while (it.hasNext()) {
            sum += ((List) it.next()).size();
        }
        return sum * 100 + m.size();
    }

    // groupingByConcurrent(classifier, counting): counts summed, plus bucket count.
    public static int gbcCounting() {
        Object r = Stream.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).collect(
                Collectors.groupingByConcurrent(x -> ((Integer) x) % 3, Collectors.counting()));
        ConcurrentHashMap m = (ConcurrentHashMap) r;
        long total = 0L;
        java.util.Iterator it = m.values().iterator();
        while (it.hasNext()) {
            total += ((Long) it.next()).longValue();
        }
        return (int) (total * 100 + m.size());
    }

    // groupingBy(classifier, mapping(f, toList)): key 0/1 -> count of mapped values.
    public static int groupingByMapping() {
        Object r = Stream.of(1, 2, 3, 4, 5, 6, 7, 8).collect(
                Collectors.groupingBy(x -> ((Integer) x) % 2,
                        Collectors.mapping(x -> ((Integer) x) * 10, Collectors.toList())));
        java.util.Map m = (java.util.Map) r;
        int evens = ((List) m.get(Integer.valueOf(0))).size();
        int odds = ((List) m.get(Integer.valueOf(1))).size();
        return evens * 100 + odds;
    }

    // groupingBy(classifier, mapping(f, summingInt)): summed mapped values per bucket.
    public static int groupingByMappingSum() {
        Object r = Stream.of(1, 2, 3, 4, 5, 6).collect(
                Collectors.groupingBy(x -> ((Integer) x) % 2,
                        Collectors.mapping(x -> ((Integer) x),
                                Collectors.summingInt(x -> ((Integer) x)))));
        java.util.Map m = (java.util.Map) r;
        int even = ((Integer) m.get(Integer.valueOf(0))).intValue(); // 2+4+6=12
        int odd = ((Integer) m.get(Integer.valueOf(1))).intValue();  // 1+3+5=9
        return even * 100 + odd;
    }

    // partitioningBy(predicate): true/false bucket sizes.
    public static int partitioningSizes() {
        Object r = Stream.of(1, 2, 3, 4, 5, 6, 7).collect(
                Collectors.partitioningBy(x -> ((Integer) x) % 2 == 0));
        java.util.Map m = (java.util.Map) r;
        int trues = ((List) m.get(Boolean.TRUE)).size();
        int falses = ((List) m.get(Boolean.FALSE)).size();
        return trues * 100 + falses;
    }

    // partitioningBy(predicate, counting): true/false counts.
    public static int partitioningCounting() {
        Object r = Stream.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).collect(
                Collectors.partitioningBy(x -> ((Integer) x) > 5, Collectors.counting()));
        java.util.Map m = (java.util.Map) r;
        long trues = ((Long) m.get(Boolean.TRUE)).longValue();
        long falses = ((Long) m.get(Boolean.FALSE)).longValue();
        return (int) (trues * 100 + falses);
    }

    // partitioningBy(predicate, toList): empty true-bucket still present.
    public static int partitioningEmptyBucket() {
        Object r = Stream.of(1, 2, 3).collect(
                Collectors.partitioningBy(x -> ((Integer) x) > 100, Collectors.toList()));
        java.util.Map m = (java.util.Map) r;
        int trues = ((List) m.get(Boolean.TRUE)).size();
        int falses = ((List) m.get(Boolean.FALSE)).size();
        return m.size() * 1000 + trues * 100 + falses;
    }

    // groupingBy(classifier, TreeMap::new, toList): ordered keys checksum.
    public static int groupingByTreeMapKeys() {
        Object r = Stream.of(5, 3, 1, 4, 2, 6, 9, 7, 8).collect(
                Collectors.groupingBy(x -> ((Integer) x) % 4,
                        () -> new TreeMap(), Collectors.toList()));
        TreeMap m = (TreeMap) r;
        int acc = 0;
        java.util.Iterator it = m.keySet().iterator();
        while (it.hasNext()) {
            Integer k = (Integer) it.next();
            int size = ((List) m.get(k)).size();
            acc = acc * 31 + k.intValue() * 10 + size;
        }
        return acc;
    }

    // groupingBy(classifier, TreeMap::new, counting): ordered key/count checksum.
    public static int groupingByTreeMapCounting() {
        Object r = Stream.of(10, 20, 21, 30, 31, 32, 40, 41, 42, 43).collect(
                Collectors.groupingBy(x -> ((Integer) x) / 10,
                        () -> new TreeMap(), Collectors.counting()));
        TreeMap m = (TreeMap) r;
        int acc = 0;
        java.util.Iterator it = m.keySet().iterator();
        while (it.hasNext()) {
            Integer k = (Integer) it.next();
            long cnt = ((Long) m.get(k)).longValue();
            acc = acc * 31 + k.intValue() * 10 + (int) cnt;
        }
        return acc;
    }

    // reducing(identity, op): product.
    public static int reducingProduct() {
        Object r = Stream.of(1, 2, 3, 4, 5).collect(
                Collectors.reducing(Integer.valueOf(1),
                        (a, b) -> Integer.valueOf(((Integer) a) * ((Integer) b))));
        return ((Integer) r).intValue();
    }

    // reducing(identity, op): sum with identity 100.
    public static int reducingSum() {
        Object r = Stream.of(1, 2, 3, 4, 5, 6).collect(
                Collectors.reducing(Integer.valueOf(100),
                        (a, b) -> Integer.valueOf(((Integer) a) + ((Integer) b))));
        return ((Integer) r).intValue();
    }
}
