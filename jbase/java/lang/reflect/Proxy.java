package java.lang.reflect;

public class Proxy {
    protected Proxy() {}

    public static native Object newProxyInstance(ClassLoader loader, Class[] interfaces, InvocationHandler h);
}
