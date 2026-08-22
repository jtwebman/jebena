package java.net;

import java.io.IOException;
import java.io.OutputStream;

/** OutputStream over a Socket's native write0. */
class SocketOutputStream extends OutputStream {
    private final Socket socket;

    SocketOutputStream(Socket socket) {
        this.socket = socket;
    }

    public void write(int b) throws IOException {
        byte[] one = new byte[] { (byte) b };
        if (Socket.write0(socket.fd(), one, 0, 1) < 0) {
            throw new IOException("write failed");
        }
    }

    public void write(byte[] b, int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        }
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        }
        if (len == 0) {
            return;
        }
        if (Socket.write0(socket.fd(), b, off, len) < 0) {
            throw new IOException("write failed");
        }
    }
}
