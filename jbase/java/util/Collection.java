package java.util;

public interface Collection extends java.lang.Iterable {
    int size();

    boolean isEmpty();

    boolean contains(Object o);

    boolean add(Object e);

    boolean remove(Object o);

    void clear();

    Iterator iterator();

    default java.util.stream.Stream stream() {
        java.util.ArrayList tmp = new java.util.ArrayList();
        for (Object o : this) {
            tmp.add(o);
        }
        return java.util.stream.Stream.ofList(tmp);
    }
}
