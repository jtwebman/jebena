package java.util;

public interface Map {
    int size();

    boolean isEmpty();

    Object get(Object key);

    Object put(Object key, Object value);

    Object remove(Object key);

    boolean containsKey(Object key);

    boolean containsValue(Object value);

    void clear();

    Set keySet();

    Collection values();

    Set entrySet();

    interface Entry {
        Object getKey();

        Object getValue();
    }
}
