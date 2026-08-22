package java.lang;

/**
 * Clean-room java.lang.Throwable for Jebena (SE25 spec, minimal). Holds the detail
 * message, a captured stack trace, and an optional cause. The stack trace is filled
 * by the native fillInStackTrace(), invoked from the constructors like the real JDK.
 * The static EMPTY initializer forces java.lang.StackTraceElement to load during
 * <clinit>, so the native never triggers class loading (hence never a GC) mid-capture.
 * Following the JDK, `cause` starts as `this` to mean "not yet initialized".
 */
public class Throwable {
    private static final StackTraceElement[] EMPTY = new StackTraceElement[0];

    private String detailMessage;
    private StackTraceElement[] stackTrace;
    private Throwable cause = this;

    public Throwable() {
        fillInStackTrace();
    }

    public Throwable(String message) {
        this.detailMessage = message;
        fillInStackTrace();
    }

    public Throwable(String message, Throwable cause) {
        this.detailMessage = message;
        this.cause = cause;
        fillInStackTrace();
    }

    public Throwable(Throwable cause) {
        this.detailMessage = (cause == null) ? null : cause.toString();
        this.cause = cause;
        fillInStackTrace();
    }

    public String getMessage() {
        return detailMessage;
    }

    public String getLocalizedMessage() {
        return getMessage();
    }

    public Throwable getCause() {
        return (cause == this) ? null : cause;
    }

    public Throwable initCause(Throwable cause) {
        if (this.cause != this) {
            throw new IllegalStateException("Can't overwrite cause with " + String.valueOf(cause));
        }
        if (cause == this) {
            throw new IllegalArgumentException("Self-causation not permitted");
        }
        this.cause = cause;
        return this;
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
        Throwable c = getCause();
        if (c != null) {
            c.printEnclosed(s, (trace != null) ? trace : EMPTY);
        }
    }

    /** Print this throwable as a "Caused by:" entry, eliding frames in common with
     *  the enclosing trace (mirrors the JDK's printEnclosedStackTrace). */
    private void printEnclosed(java.io.PrintStream s, StackTraceElement[] enclosing) {
        StackTraceElement[] trace = (stackTrace != null) ? stackTrace : EMPTY;
        int m = trace.length - 1;
        int n = enclosing.length - 1;
        while (m >= 0 && n >= 0 && trace[m].equals(enclosing[n])) {
            m--;
            n--;
        }
        int common = trace.length - 1 - m;
        s.println("Caused by: " + toString());
        for (int i = 0; i <= m; i++) {
            s.println("\tat " + trace[i]);
        }
        if (common != 0) {
            s.println("\t... " + common + " more");
        }
        Throwable c = getCause();
        if (c != null) {
            c.printEnclosed(s, trace);
        }
    }

    public String toString() {
        String n = getClass().getName();
        String m = detailMessage;
        return (m != null) ? (n + ": " + m) : n;
    }
}
