package java.io;

/**
 * Clean-room java.io.UncheckedIOException. Wraps an IOException in an unchecked
 * RuntimeException so it can cross APIs that do not declare it. The originating
 * IOException is stored and returned from getCause().
 */
public class UncheckedIOException extends RuntimeException {

    private final IOException cause;

    public UncheckedIOException(String message, IOException cause) {
        super(message);
        this.cause = cause;
    }

    // Our clean-room Throwable does not define getCause(), so this simply adds
    // the accessor with the IOException-narrowed return type java.io promises.
    public IOException getCause() {
        return cause;
    }
}
