package java.lang.reflect;

public final class Constructor {
    private int vmClassIndex;
    private int vmIndex;

    private Constructor() {}

    public native int getParameterCount();

    public native Object newInstance(Object[] args);
}
