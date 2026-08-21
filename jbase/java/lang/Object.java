package java.lang;

/**
 * Clean-room java.lang.Object for Jebena, written from the JLS/JVMS SE25 spec.
 * The root of the class hierarchy. Methods that require VM support (identity
 * hash, the runtime class, threading) are declared native and dispatched to the
 * Zig native-method registry; the rest is ordinary bytecode.
 */
public class Object {

    public Object() {}

    public boolean equals(Object obj) {
        return this == obj;
    }

    public int hashCode() {
        return identityHashCode(this);
    }

    public native Class getClass();

    public String toString() {
        return getClass().getName() + "@" + Integer.toHexString(hashCode());
    }

    // VM-provided: a stable identity hash for the object. Wired to Zig until the
    // real object-header hash lands.
    static native int identityHashCode(Object o);
}
