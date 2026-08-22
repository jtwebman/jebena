package java.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Clean-room minimal java.net.Socket over the VM's std.Io TCP natives. Holds the
 * native socket handle (fd) as a long; -1 means unusable. Blocking connect/read/
 * write run in the VM natives which PARK the carrier at a safepoint for the OS
 * call (so a concurrent GC is not blocked) but do NOT yet yield the fiber -- see
 * docs/THREADING.md "networking". read0/write0 are static so the stream wrappers
 * can call them with just the fd.
 */
public class Socket {
    long fd;
    private boolean closed;
    private InputStream in;
    private OutputStream out;

    public Socket(String host, int port) throws IOException {
        this.fd = connect0(host, port);
        if (this.fd < 0) {
            throw new IOException("connect failed: " + host + ":" + port);
        }
    }

    // Package-private: wrap a handle produced by ServerSocket.accept.
    Socket(long fd) {
        this.fd = fd;
    }

    long fd() {
        return fd;
    }

    public InputStream getInputStream() throws IOException {
        if (closed) {
            throw new IOException("socket closed");
        }
        if (in == null) {
            in = new SocketInputStream(this);
        }
        return in;
    }

    public OutputStream getOutputStream() throws IOException {
        if (closed) {
            throw new IOException("socket closed");
        }
        if (out == null) {
            out = new SocketOutputStream(this);
        }
        return out;
    }

    public boolean isClosed() {
        return closed;
    }

    public synchronized void close() throws IOException {
        if (!closed) {
            closed = true;
            close0(fd);
        }
    }

    private static native long connect0(String host, int port);

    static native void close0(long fd);

    // Reads up to len bytes into b[off..]; returns count read, or -1 at EOF.
    static native int read0(long fd, byte[] b, int off, int len);

    // Writes all len bytes of b[off..]; returns bytes written, or -1 on error.
    static native int write0(long fd, byte[] b, int off, int len);
}
