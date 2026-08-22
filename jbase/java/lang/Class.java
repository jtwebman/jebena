package java.lang;

/**
 * Minimal clean-room java.lang.Class (the runtime class mirror). Instances are
 * built by the VM (Object.getClass), one per loaded class; vmIndex identifies the
 * class in the loader. getName/getSimpleName are native.
 */
public final class Class {
    private int vmIndex;

    private Class() {}

    public native String getName();

    public native String getSimpleName();

    public native java.lang.reflect.Method[] getDeclaredMethods();

    public native java.lang.reflect.Field[] getDeclaredFields();

    public native java.lang.reflect.Constructor[] getDeclaredConstructors();

    public native boolean isAnnotationPresent(Class annotationClass);

    public native ClassLoader getClassLoader();

    public native java.lang.annotation.Annotation getAnnotation(Class annotationClass);

    public native boolean isInterface();

    public native boolean isPrimitive();

    public native Object[] getEnumConstants();

    static native Class getPrimitiveClass(String name);

    public native Class getSuperclass();

    public native Class[] getInterfaces();

    public native java.lang.reflect.Field getDeclaredField(String name) throws NoSuchFieldException;

    public native java.lang.reflect.Method getDeclaredMethod(String name, Class[] parameterTypes) throws NoSuchMethodException;

    public static native Class forName(String className) throws ClassNotFoundException;

    public String toString() {
        return "class " + getName();
    }
}
