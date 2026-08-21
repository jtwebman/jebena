package java.lang;

public final class System {
    private System() {}

    public static final java.io.PrintStream out = new java.io.PrintStream(1);
    public static final java.io.PrintStream err = new java.io.PrintStream(2);

    public static native void arraycopy(Object src, int srcPos, Object dest, int destPos, int length);

    public static native long currentTimeMillis();

    public static native long nanoTime();

    public static native int identityHashCode(Object x);

    public static String lineSeparator() {
        return "\n";
    }
}
