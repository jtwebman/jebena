package java.util;

public interface List extends Collection {
    Object get(int index);

    Object set(int index, Object element);

    Object remove(int index);

    int indexOf(Object o);
}
