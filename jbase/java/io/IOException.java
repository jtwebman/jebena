package java.io;

/**
 * Clean-room java.io.IOException. The checked base of the I/O exception
 * hierarchy: a plain Exception carrying an optional detail message.
 */
public class IOException extends Exception {

    public IOException() {
        super();
    }

    public IOException(String message) {
        super(message);
    }
}
