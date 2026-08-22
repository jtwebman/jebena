package java.lang;

/**
 * Clean-room java.lang.StackTraceElement: one frame of a Throwable's stack trace.
 * The VM's fillInStackTrace native populates these directly (bypassing the
 * constructor) with the declaring class (binary name, dotted), method name, source
 * file (or null), and line number (or -1 when unknown).
 */
public final class StackTraceElement {
    private String declaringClass;
    private String methodName;
    private String fileName;
    private int lineNumber;

    public StackTraceElement(String declaringClass, String methodName, String fileName, int lineNumber) {
        this.declaringClass = declaringClass;
        this.methodName = methodName;
        this.fileName = fileName;
        this.lineNumber = lineNumber;
    }

    public String getClassName() {
        return declaringClass;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getFileName() {
        return fileName;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String toString() {
        String base = declaringClass + "." + methodName;
        if (fileName != null) {
            if (lineNumber >= 0) {
                return base + "(" + fileName + ":" + lineNumber + ")";
            }
            return base + "(" + fileName + ")";
        }
        return base + "(Unknown Source)";
    }
}
