package java.lang;

/**
 * Clean-room java.lang.Throwable for Jebena (SE25 spec, minimal). Holds the detail
 * message and a captured stack trace. The stack trace is filled by the native
 * fillInStackTrace(), invoked from the constructors like the real JDK. The static
 * EMPTY initializer forces java.lang.StackTraceElement to load during <clinit>, so
 * the native never triggers class loading (hence never a GC) mid-capture.
 */
public class Throwable {
    private static final StackTraceElement[] EMPTY = new StackTraceElement[0];

    private String detailMessage;
    private StackTraceElement[] stackTrace;

    public Throwable() {
        fillInStackTrace();
    }

    public Throwable(String message) {
        this.detailMessage = message;
        fillInStackTrace();
    }

    public String getMessage() {
        return detailMessage;
    }

    public String getLocalizedMessage() {
        return getMessage();
    }

    /** Capture the current call stack into this throwable (VM native). */
    public native Throwable fillInStackTrace();

    public StackTraceElement[] getStackTrace() {
        StackTraceElement[] trace = stackTrace;
        if (trace == null) {
            return EMPTY;
        }
        StackTraceElement[] copy = new StackTraceElement[trace.length];
        for (int i = 0; i < trace.length; i++) {
            copy[i] = trace[i];
        }
        return copy;
    }

    public void printStackTrace() {
        java.io.PrintStream s = System.err;
        s.println(toString());
        StackTraceElement[] trace = stackTrace;
        if (trace != null) {
            for (int i = 0; i < trace.length; i++) {
                s.println("\tat " + trace[i]);
            }
        }
    }

    public String toString() {
        String n = getClass().getName();
        String m = detailMessage;
        return (m != null) ? (n + ": " + m) : n;
    }
}
