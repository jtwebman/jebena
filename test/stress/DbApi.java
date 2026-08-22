package st;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * The capstone: a Postgres-backed HTTP API running on jebena. An HTTP server
 * fiber accepts a GET, opens a Postgres connection (reusing PgQuery's v3 wire
 * client), runs "SELECT 1", and returns the value in the HTTP body ("db=1"). The
 * client (main) fiber GETs "/", verifies the status line + body, and returns the
 * body byte-sum (308). This ties together java.net sockets + hand-written HTTP +
 * the PG wire protocol end to end.
 *
 * demo() wires the HTTP server to an in-process spec-faithful MOCK PG backend
 * (PgQuery.MockServer) so it is always green with no external DB. real() points
 * the HTTP server at a live Postgres on 127.0.0.1:5432 (pg-... run by
 * dbapi-stress.sh only under JEBENA_PGTEST). Three fibers block concurrently
 * (HTTP client, HTTP server, PG backend) and blocking I/O holds a carrier (no
 * fiber parking yet), so this needs JEBENA_CARRIERS >= 3. See docs/THREADING.md.
 */
public class DbApi {
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

    /** HTTP server that answers each GET by querying Postgres for SELECT 1. */
    static final class HttpServer extends Thread {
        final ServerSocket httpSS;
        final int pgPort;
        final String user;
        final String db;
        volatile int err;

        HttpServer(ServerSocket httpSS, int pgPort, String user, String db) {
            this.httpSS = httpSS;
            this.pgPort = pgPort;
            this.user = user;
            this.db = db;
        }

        public void run() {
            try {
                Socket c = httpSS.accept();
                InputStream in = c.getInputStream();
                OutputStream out = c.getOutputStream();
                byte[] buf = new byte[2048];
                readAll(in, buf, true); // consume the request headers

                // Backing query: open a PG connection and run SELECT 1.
                Socket pg = new Socket("127.0.0.1", pgPort);
                int val = PgQuery.runClient(pg.getInputStream(), pg.getOutputStream(), user, db);
                pg.close();

                String body = "db=" + val;
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

    // Shared client: GET "/" from the HTTP server, verify, return body byte-sum.
    static int httpGet(int httpPort) throws Exception {
        Socket s = new Socket("127.0.0.1", httpPort);
        OutputStream out = s.getOutputStream();
        InputStream in = s.getInputStream();
        byte[] req = ascii("GET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n");
        out.write(req, 0, req.length);
        out.flush();
        byte[] buf = new byte[2048];
        int total = readAll(in, buf, false);
        s.close();

        int he = headerEnd(buf, total);
        if (he < 0) {
            return -4;
        }
        if (!str(buf, 0, he).startsWith("HTTP/1.1 200 OK")) {
            return -5;
        }
        int bodyOff = he + 4;
        String body = str(buf, bodyOff, total - bodyOff);
        if (!body.equals("db=1")) {
            return -6;
        }
        int sum = 0;
        for (int k = bodyOff; k < total; k++) {
            sum += (buf[k] & 0xff);
        }
        return sum; // "db=1" = 308
    }

    // Always-green: HTTP server backed by an in-process mock PG (needs carriers>=3).
    public static int demo() {
        try {
            ServerSocket pgSS = new ServerSocket(0);
            int pgPort = pgSS.getLocalPort();
            PgQuery.MockServer pg = new PgQuery.MockServer(pgSS);
            pg.start();

            ServerSocket httpSS = new ServerSocket(0);
            int httpPort = httpSS.getLocalPort();
            HttpServer http = new HttpServer(httpSS, pgPort, "test", "test");
            http.start();

            int r = httpGet(httpPort);

            http.join();
            pg.join();
            httpSS.close();
            if (http.err != 0 || pg.err != 0) {
                return -3;
            }
            return r; // 308
        } catch (Exception e) {
            return -1;
        }
    }

    // Live DB: HTTP server backed by real Postgres on 127.0.0.1:5432 (trust).
    // Run by dbapi-stress.sh only when JEBENA_PGTEST is set and a DB is reachable.
    public static int real() {
        try {
            ServerSocket httpSS = new ServerSocket(0);
            int httpPort = httpSS.getLocalPort();
            HttpServer http = new HttpServer(httpSS, 5432, "postgres", "postgres");
            http.start();

            int r = httpGet(httpPort);

            http.join();
            httpSS.close();
            if (http.err != 0) {
                return -3;
            }
            return r; // 308
        } catch (Exception e) {
            return -1;
        }
    }
}
