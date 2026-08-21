package java.util;

public interface Deque extends Queue {
    void addFirst(Object e);

    void addLast(Object e);

    Object removeFirst();

    Object removeLast();

    Object pollFirst();

    Object pollLast();

    Object peekFirst();

    Object peekLast();

    Object getFirst();

    Object getLast();

    void push(Object e);

    Object pop();
}
