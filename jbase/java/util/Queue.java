package java.util;

public interface Queue extends Collection {
    boolean offer(Object e);

    Object poll();

    Object peek();

    Object remove();

    Object element();
}
