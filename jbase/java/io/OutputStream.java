package java.io;

/**
 * Clean-room java.io.OutputStream. Abstract byte-sink base: subclasses supply the
 * single-byte write(int); the bulk overload here loops over it. flush and close
 * default to no-ops. Signatures declare throws IOException to match the platform.
 */
public abstract class OutputStream {

    public abstract void write(int b) throws IOException;

    public void write(byte[] b, int off, int len) throws IOException {
        for (int i = 0; i < len; i++) {
            write(b[off + i]);
        }
    }

    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    public void flush() throws IOException {}

    public void close() throws IOException {}
}
