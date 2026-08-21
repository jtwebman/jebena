package java.util;

public interface NavigableMap extends SortedMap {
    Object ceilingKey(Object key);

    Object floorKey(Object key);

    Object higherKey(Object key);

    Object lowerKey(Object key);
}
