package java.util;

class SimpleEntry implements Map.Entry {
    private final Object key;
    private final Object value;

    SimpleEntry(Object key, Object value) {
        this.key = key;
        this.value = value;
    }

    public Object getKey() {
        return key;
    }

    public Object getValue() {
        return value;
    }
}
