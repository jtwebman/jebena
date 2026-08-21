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

    public String toString() {
        return "class " + getName();
    }
}
