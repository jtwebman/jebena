package java.lang.reflect;

public final class Field {
    private int vmClassIndex;
    private int vmIndex;

    private Field() {}

    public native String getName();

    public native Object get(Object obj);

    public native void set(Object obj, Object value);

    public native boolean isAnnotationPresent(Class annotationClass);

    public native java.lang.annotation.Annotation getAnnotation(Class annotationClass);

    public String toString() {
        return getName();
    }
}
