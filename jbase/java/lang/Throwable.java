package java.lang;

/**
 * Clean-room java.lang.Throwable for Jebena (SE25 spec, minimal). Holds the detail
 * message; stack-trace capture (fillInStackTrace) and cause chaining will arrive
 * once we have arrays/StackTraceElement. Constructors and getMessage are ordinary
 * bytecode operating on the detailMessage instance field.
 */
public class Throwable {
    private String detailMessage;

    public Throwable() {}

    public Throwable(String message) {
        this.detailMessage = message;
    }

    public String getMessage() {
        return detailMessage;
    }

    public String getLocalizedMessage() {
        return getMessage();
    }
}
