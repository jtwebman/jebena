package st;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * A small multi-endpoint HTTP service on jebena -- a mini "web app" demonstrating
 * path routing plus a Postgres-backed endpoint, all on the fiber-parking sockets
 * (so it runs at carriers=1). The server fiber handles three requests in a loop
 * and dispatches on the request path:
 *   GET /ping  -> 200 "pong"
 *   GET /db    -> open a Postgres connection, SELECT 1, 200 "db=1"
 *   (other)    -> 404 "not found"
 * The client (main) fiber issues the three requests and returns a checksum
 * (1 for /ping + 10 for /db + 100 for the 404) = 111 when all routes behave.
 * Matches real java. demo() backs /db with a mock PG; real() uses a live DB.
 */
public class RouterApp {
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

    static int headerEnd(byte[] b, int len) {
        for (int i = 0; i + 3 < len; i++) {
            if (b[i] == 13 && b[i + 1] == 10 && b[i + 2] == 13 && b[i + 3] == 10) {
                return i;
            }
        }
        return -1;
    }

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

    static void respond(OutputStream out, int code, String reason, String body) throws Exception {
        String resp = "HTTP/1.1 " + code + " " + reason + "\r\nContent-Length: " + body.length()
                + "\r\nConnection: close\r\n\r\n" + body;
        byte[] rb = ascii(resp);
        out.write(rb, 0, rb.length);
        out.flush();
    }

    // Extract the request path (second token of the request line).
    static String path(byte[] buf, int total) {
        int i = 0;
        while (i < total && buf[i] != ' ') {
            i++;
        }
        int ps = i + 1;
        int j = ps;
        while (j < total && buf[j] != ' ') {
            j++;
        }
        return str(buf, ps, j - ps);
    }

    static final class Server extends Thread {
        final ServerSocket ss;
        final int pgPort;
        final String user;
        final String db;
        final int requests;
        volatile int err;

        Server(ServerSocket ss, int pgPort, String user, String db, int requests) {
            this.ss = ss;
            this.pgPort = pgPort;
            this.user = user;
            this.db = db;
            this.requests = requests;
        }

        public void run() {
            try {
                for (int r = 0; r < requests; r++) {
                    Socket c = ss.accept();
                    InputStream in = c.getInputStream();
                    OutputStream out = c.getOutputStream();
                    byte[] buf = new byte[2048];
                    int total = readAll(in, buf, true);
                    String p = path(buf, total);
                    if (p.equals("/ping")) {
                        respond(out, 200, "OK", "pong");
                    } else if (p.equals("/db")) {
                        Socket pg = new Socket("127.0.0.1", pgPort);
                        int v = PgQuery.runClient(pg.getInputStream(), pg.getOutputStream(), user, db);
                        pg.close();
                        respond(out, 200, "OK", "db=" + v);
                    } else {
                        respond(out, 404, "Not Found", "not found");
                    }
                    c.close();
                }
            } catch (Exception e) {
                err = 1;
            }
        }
    }

    // GET one path; return the response (status line + body) as a String.
    static String get(int port, String path) throws Exception {
        Socket s = new Socket("127.0.0.1", port);
        OutputStream out = s.getOutputStream();
        InputStream in = s.getInputStream();
        byte[] req = ascii("GET " + path + " HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n");
        out.write(req, 0, req.length);
        out.flush();
        byte[] buf = new byte[2048];
        int total = readAll(in, buf, false);
        s.close();
        return str(buf, 0, total);
    }

    static boolean statusAndBody(String resp, String statusNeedle, String body) {
        int he = -1;
        // find CRLFCRLF in the String
        for (int i = 0; i + 3 < resp.length(); i++) {
            if (resp.charAt(i) == '\r' && resp.charAt(i + 1) == '\n'
                    && resp.charAt(i + 2) == '\r' && resp.charAt(i + 3) == '\n') {
                he = i;
                break;
            }
        }
        if (he < 0) {
            return false;
        }
        String head = resp.substring(0, he);
        String b = resp.substring(he + 4);
        return head.startsWith(statusNeedle) && b.equals(body);
    }

    static int run(int pgPort, String user, String db) {
        try {
            ServerSocket ss = new ServerSocket(0);
            int port = ss.getLocalPort();
            Server server = new Server(ss, pgPort, user, db, 3);
            server.start();

            int score = 0;
            if (statusAndBody(get(port, "/ping"), "HTTP/1.1 200 OK", "pong")) {
                score += 1;
            }
            if (statusAndBody(get(port, "/db"), "HTTP/1.1 200 OK", "db=1")) {
                score += 10;
            }
            if (statusAndBody(get(port, "/nope"), "HTTP/1.1 404 Not Found", "not found")) {
                score += 100;
            }

            server.join();
            ss.close();
            if (server.err != 0) {
                return -2;
            }
            return score; // 111 when all three routes behave
        } catch (Exception e) {
            return -1;
        }
    }

    public static int demo() {
        try {
            // Mock PG backend for the /db route (always green, no external DB).
            ServerSocket pgSS = new ServerSocket(0);
            int pgPort = pgSS.getLocalPort();
            PgQuery.MockServer pg = new PgQuery.MockServer(pgSS);
            pg.start();

            int r = run(pgPort, "test", "test");

            pg.join();
            pgSS.close();
            if (pg.err != 0) {
                return -3;
            }
            return r; // 111
        } catch (Exception e) {
            return -1;
        }
    }

    public static int real() {
        // /db route backed by a live Postgres on 127.0.0.1:5432.
        return run(5432, "postgres", "postgres"); // 111
    }
}
