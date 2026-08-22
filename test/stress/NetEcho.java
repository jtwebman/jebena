package st;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Loopback TCP echo. A server fiber accepts one connection and echoes whatever
 * it reads; the main fiber connects, sends "PINGpong123", reads the echo back,
 * and returns the byte-sum (888) -- deterministic, and it matches real java
 * (which has real java.net), so the differential harness can check it.
 *
 * Blocking accept/read occupy the carrier for the syscall (no fiber parking yet),
 * so the server fiber and the client (main) fiber must run on DIFFERENT carriers:
 * run with JEBENA_CARRIERS >= 2. See docs/THREADING.md "networking".
 */
public class NetEcho {
    static final class Server extends Thread {
        final ServerSocket ss;
        volatile int err;

        Server(ServerSocket ss) {
            this.ss = ss;
        }

        public void run() {
            try {
                Socket c = ss.accept();
                InputStream in = c.getInputStream();
                OutputStream out = c.getOutputStream();
                byte[] buf = new byte[64];
                int n = in.read(buf, 0, buf.length);
                if (n > 0) {
                    out.write(buf, 0, n);
                    out.flush();
                }
                c.close();
            } catch (Exception e) {
                err = 1;
            }
        }
    }

    public static int demo() {
        try {
            ServerSocket ss = new ServerSocket(0);
            int port = ss.getLocalPort();
            Server server = new Server(ss);
            server.start();

            Socket s = new Socket("127.0.0.1", port);
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();
            byte[] msg = new byte[] { 80, 73, 78, 71, 112, 111, 110, 103, 49, 50, 51 }; // "PINGpong123"
            out.write(msg, 0, msg.length);
            out.flush();

            byte[] rbuf = new byte[64];
            int total = 0;
            int sum = 0;
            while (total < msg.length) {
                int n = in.read(rbuf, 0, rbuf.length);
                if (n < 0) {
                    break;
                }
                for (int i = 0; i < n; i++) {
                    sum += (rbuf[i] & 0xff);
                }
                total += n;
            }
            s.close();
            server.join();
            ss.close();
            if (server.err != 0) {
                return -1;
            }
            return sum; // 888
        } catch (Exception e) {
            return -2;
        }
    }
}
