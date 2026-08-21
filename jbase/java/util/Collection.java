package java.util;

public interface Collection extends java.lang.Iterable {
    int size();

    boolean isEmpty();

    boolean contains(Object o);

    boolean add(Object e);

    boolean remove(Object o);

    void clear();

    Iterator iterator();
}
