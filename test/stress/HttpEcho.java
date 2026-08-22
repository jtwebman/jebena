package st;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Minimal hand-written HTTP/1.1 over the java.net sockets. A server fiber accepts
 * one connection, reads the request until the CRLFCRLF header terminator, parses
 * the request line, and replies "HTTP/1.1 200 OK" with a Content-Length body that
 * echoes the requested path ("path=/hi"); it sends Connection: close and closes.
 * The client (main) fiber GETs "/hi", reads to EOF, checks the status line and
 * body, and returns the body byte-sum (746). Deterministic and it matches real
 * java (which has real java.net), so the differential harness can check it.
 *
 * Blocking accept/read hold the carrier (no fiber parking yet), so server + client
 * need different carriers: run with JEBENA_CARRIERS >= 2. See docs/THREADING.md.
 */
public class HttpEcho {
    static byte[] ascii(String s) {
        byte[] b = new byte[s.length()];
        for (int i = 0; i < s.length(); i++) {
            b[i] = (byte) s.charAt(i);
        }
        return b;
    }

    static String str(byte[] b, int off, int len) {
        char[] c = new char[len];
        for (int i = 0; i < len; i++) {
            c[i] = (char) (b[off + i] & 0xff);
        }
        return new String(c);
    }

    // Index of the CRLFCRLF header terminator within b[0..len), or -1.
    static int headerEnd(byte[] b, int len) {
        for (int i = 0; i + 3 < len; i++) {
            if (b[i] == 13 && b[i + 1] == 10 && b[i + 2] == 13 && b[i + 3] == 10) {
                return i;
            }
        }
        return -1;
    }

    // Read until CRLFCRLF is present (server) or EOF (client). Returns total bytes.
    static int readAll(InputStream in, byte[] buf, boolean untilHeader) throws Exception {
        int total = 0;
        while (total < buf.length) {
            int n = in.read(buf, total, buf.length - total);
            if (n < 0) {
                break;
            }
            total += n;
            if (untilHeader && headerEnd(buf, total) >= 0) {
                break;
            }
        }
        return total;
    }

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
                byte[] buf = new byte[2048];
                int total = readAll(in, buf, true);

                // Parse the request line: METHOD SP path SP HTTP/1.1
                int i = 0;
                while (i < total && buf[i] != ' ') {
                    i++;
                }
                int ps = i + 1;
                int j = ps;
                while (j < total && buf[j] != ' ') {
                    j++;
                }
                String path = str(buf, ps, j - ps);

                String body = "path=" + path;
                String resp = "HTTP/1.1 200 OK\r\nContent-Length: " + body.length()
                        + "\r\nConnection: close\r\n\r\n" + body;
                byte[] rb = ascii(resp);
                out.write(rb, 0, rb.length);
                out.flush();
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
            byte[] req = ascii("GET /hi HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n");
            out.write(req, 0, req.length);
            out.flush();

            byte[] buf = new byte[2048];
            int total = readAll(in, buf, false); // read to EOF (server sends Connection: close)
            s.close();
            server.join();
            ss.close();
            if (server.err != 0) {
                return -1;
            }

            int he = headerEnd(buf, total);
            if (he < 0) {
                return -2;
            }
            String statusLine = str(buf, 0, he);
            if (!statusLine.startsWith("HTTP/1.1 200 OK")) {
                return -3;
            }
            int bodyOff = he + 4;
            String body = str(buf, bodyOff, total - bodyOff);
            if (!body.equals("path=/hi")) {
                return -4;
            }
            int sum = 0;
            for (int k = bodyOff; k < total; k++) {
                sum += (buf[k] & 0xff);
            }
            return sum; // "path=/hi" = 746
        } catch (Exception e) {
            return -5;
        }
    }
}
