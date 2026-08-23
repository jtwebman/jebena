package java.util;

class ArrayListItr implements Iterator {
    private final ArrayList list;
    private int cursor;
    private int lastRet = -1;

    ArrayListItr(ArrayList list) {
        this.list = list;
    }

    public boolean hasNext() {
        return cursor < list.size();
    }

    public Object next() {
        if (cursor >= list.size()) {
            throw new NoSuchElementException();
        }
        lastRet = cursor;
        return list.get(cursor++);
    }

    public void remove() {
        if (lastRet < 0) {
            throw new IllegalStateException();
        }
        list.remove(lastRet);
        cursor = lastRet;
        lastRet = -1;
    }
}
