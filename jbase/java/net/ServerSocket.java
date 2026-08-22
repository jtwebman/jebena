package java.net;

import java.io.IOException;

/**
 * Clean-room minimal java.net.ServerSocket over the VM's std.Io TCP natives.
 * Binds + listens on 127.0.0.1:port (port 0 = ephemeral; getLocalPort reports the
 * chosen port). accept() blocks the carrier for the OS accept (parked at a
 * safepoint so GC is not blocked); it does NOT yet yield the fiber, so a
 * concurrent server-fiber + client-fiber pattern needs >= 2 carriers today (a
 * blocking accept would monopolize a lone carrier). See docs/THREADING.md.
 */
public class ServerSocket {
    // bind0 returns (resolvedPort << 32) | (fd & 0xffffffff): the resolved port
    // comes from std.Io's bound address, so no extra getsockname syscall is
    // needed and the value is fully portable.
    private final long handle;
    private final int localPort;
    private boolean closed;

    public ServerSocket(int port) throws IOException {
        long h = bind0(port);
        if (h < 0) {
            throw new IOException("bind failed on port " + port);
        }
        this.handle = h;
        this.localPort = (int) (h >>> 32);
    }

    private long fd() {
        return handle & 0xffffffffL;
    }

    public Socket accept() throws IOException {
        if (closed) {
            throw new IOException("socket closed");
        }
        long client = accept0(fd());
        if (client < 0) {
            throw new IOException("accept failed");
        }
        return new Socket(client);
    }

    public int getLocalPort() {
        return localPort;
    }

    public boolean isClosed() {
        return closed;
    }

    public synchronized void close() throws IOException {
        if (!closed) {
            closed = true;
            close0(fd());
        }
    }

    private static native long bind0(int port);

    private static native long accept0(long fd);

    private static native void close0(long fd);
}
