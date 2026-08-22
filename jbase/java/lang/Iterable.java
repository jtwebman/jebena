package java.lang;

public interface Iterable {
    java.util.Iterator iterator();

    default void forEach(java.util.function.Consumer action) {
        java.util.Iterator it = iterator();
        while (it.hasNext()) {
            action.accept(it.next());
        }
    }
}
