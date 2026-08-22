package java.io;

/**
 * Clean-room java.io.Writer. Abstract character-sink base: subclasses supply the
 * bulk write(char[],off,len), flush and close; the convenience overloads here all
 * funnel through it. Signatures declare throws IOException to match the platform
 * so callers compiled against real java.io link, though the in-memory subclasses
 * never actually throw.
 */
public abstract class Writer {

    public abstract void write(char[] cbuf, int off, int len) throws IOException;

    public abstract void flush() throws IOException;

    public abstract void close() throws IOException;

    public void write(int c) throws IOException {
        char[] one = new char[1];
        one[0] = (char) c;
        write(one, 0, 1);
    }

    public void write(char[] cbuf) throws IOException {
        write(cbuf, 0, cbuf.length);
    }

    public void write(String s) throws IOException {
        char[] chars = s.toCharArray();
        write(chars, 0, chars.length);
    }

    public Writer append(char c) throws IOException {
        write(c);
        return this;
    }

    public Writer append(CharSequence cs) throws IOException {
        write((cs == null) ? "null" : cs.toString());
        return this;
    }
}
