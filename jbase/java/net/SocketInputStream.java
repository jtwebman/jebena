package java.net;

import java.io.IOException;
import java.io.InputStream;

/** InputStream over a Socket's native read0. */
class SocketInputStream extends InputStream {
    private final Socket socket;

    SocketInputStream(Socket socket) {
        this.socket = socket;
    }

    public int read() throws IOException {
        byte[] one = new byte[1];
        int n = Socket.read0(socket.fd(), one, 0, 1);
        if (n <= 0) {
            return -1;
        }
        return one[0] & 0xff;
    }

    public int read(byte[] b, int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        }
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        }
        if (len == 0) {
            return 0;
        }
        int n = Socket.read0(socket.fd(), b, off, len);
        return n <= 0 ? -1 : n;
    }
}
