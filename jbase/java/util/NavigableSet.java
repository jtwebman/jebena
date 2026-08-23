package java.util;

// Minimal NavigableSet: enough of the JDK contract for the ordered
// descending key-set views to carry the correct static type. The extra
// navigation methods the JDK declares are not needed by jbase yet, so this
// interface adds nothing beyond Set for now.
public interface NavigableSet extends Set {
}
