package java.util;

/**
 * Clean-room java.util.Stack: a LIFO stack built on Vector. The top of the
 * stack is the element at the highest index. Jebena is single-threaded, so the
 * historical synchronization is omitted. EmptyStackException does not exist in
 * jbase, so the empty-stack cases throw a plain RuntimeException instead.
 */
public class Stack extends Vector {
    public Stack() {
        super();
    }

    public Object push(Object item) {
        addElement(item);
        return item;
    }

    public Object pop() {
        int len = size();
        if (len == 0) {
            throw new RuntimeException("stack empty");
        }
        Object obj = elementData[len - 1];
        removeElementAt(len - 1);
        return obj;
    }

    public Object peek() {
        int len = size();
        if (len == 0) {
            throw new RuntimeException("stack empty");
        }
        return elementData[len - 1];
    }

    public boolean empty() {
        return size() == 0;
    }

    public int search(Object o) {
        int len = size();
        for (int i = len - 1; i >= 0; i--) {
            Object e = elementData[i];
            if (o == null ? e == null : o.equals(e)) {
                return len - i;
            }
        }
        return -1;
    }
}
