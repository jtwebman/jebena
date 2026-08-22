package java.io;

/**
 * Clean-room java.io.Reader. Abstract character-source base: subclasses supply the
 * bulk read(char[],off,len) and close; the no-arg read() here reads a single char
 * through it. Signatures declare throws IOException to match the platform.
 */
public abstract class Reader {

    public abstract int read(char[] cbuf, int off, int len) throws IOException;

    public abstract void close() throws IOException;

    public int read() throws IOException {
        char[] one = new char[1];
        int n = read(one, 0, 1);
        if (n <= 0) {
            return -1;
        }
        return one[0];
    }
}
