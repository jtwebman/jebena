package java.lang.reflect;

/** A reflective method handle. vmClassIndex/vmIndex identify the method in the VM. */
public final class Method {
    private int vmClassIndex;
    private int vmIndex;

    private Method() {}

    public native String getName();

    public native int getParameterCount();

    public native Object invoke(Object receiver, Object[] args);

    public String toString() {
        return getName();
    }
}
