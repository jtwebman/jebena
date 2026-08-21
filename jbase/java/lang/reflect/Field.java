package java.lang.reflect;

public final class Field {
    private int vmClassIndex;
    private int vmIndex;

    private Field() {}

    public native String getName();

    public native Object get(Object obj);

    public native void set(Object obj, Object value);

    public String toString() {
        return getName();
    }
}
