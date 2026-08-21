package java.util;

class ArrayListItr implements Iterator {
    private final ArrayList list;
    private int cursor;

    ArrayListItr(ArrayList list) {
        this.list = list;
    }

    public boolean hasNext() {
        return cursor < list.size();
    }

    public Object next() {
        return list.get(cursor++);
    }
}
