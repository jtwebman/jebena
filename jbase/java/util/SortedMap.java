package java.util;

public interface SortedMap extends Map {
    Object firstKey();

    Object lastKey();

    Comparator comparator();
}
