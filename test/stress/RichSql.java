package st;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

/**
 * Richer SQL over the Postgres v3 wire client: a general query() that returns a
 * full result set (multiple rows, multiple typed text columns), reusing PgQuery's
 * low-level wire helpers. This proves the parser handles real RowDescription/
 * DataRow shapes, not just a single int.
 *
 * demo() runs the client against an in-process mock backend that returns a 3-row,
 * 2-column result -- (1,"r1"),(2,"r2"),(3,"r3") -- and returns a structural
 * checksum rows*10000 + cols*1000 + sum(all cell bytes) = 32642. real() runs the
 * SAME client against a live Postgres with
 *   SELECT g, 'r' || g FROM generate_series(1,3) g ORDER BY g
 * which yields the same rows and the same 32642. Both match real java.
 *
 * demo() has a mock-server fiber + client fiber, so needs JEBENA_CARRIERS >= 2.
 */
public class RichSql {
    // Startup + simple Query; returns rows (each a String[] of column text values).
    static String[][] query(InputStream in, OutputStream out, String user, String db, String sql) throws Exception {
        PgQuery.sendStartup(out, user, db);
        while (true) {
            byte[] m = PgQuery.readMsg(in);
            char t = (char) (m[0] & 0xff);
            if (t == 'R') {
                int code = PgQuery.getInt32(m, 1);
                if (code == 0) {
                    // AuthenticationOk
                } else if (code == 3) {
                    PgQuery.sendPassword(out, "");
                } else if (code == 5) {
                    throw new RuntimeException("MD5 auth not supported yet");
                } else if (code == 10) {
                    throw new RuntimeException("SCRAM auth not supported yet");
                } else {
                    throw new RuntimeException("unsupported auth " + code);
                }
            } else if (t == 'E') {
                throw new RuntimeException("server error at startup");
            } else if (t == 'Z') {
                break;
            }
        }

        PgQuery.sendQuery(out, sql);
        ArrayList rows = new ArrayList();
        while (true) {
            byte[] m = PgQuery.readMsg(in);
            char t = (char) (m[0] & 0xff);
            if (t == 'D') {
                int cols = PgQuery.getInt16(m, 1);
                String[] row = new String[cols];
                int p = 3;
                for (int i = 0; i < cols; i++) {
                    int clen = PgQuery.getInt32(m, p);
                    p += 4;
                    if (clen < 0) {
                        row[i] = null; // SQL NULL
                    } else {
                        row[i] = PgQuery.str(m, p, clen);
                        p += clen;
                    }
                }
                rows.add(row);
            } else if (t == 'E') {
                throw new RuntimeException("server error on query");
            } else if (t == 'Z') {
                break;
            }
            // 'T' RowDescription / 'C' CommandComplete: ignored here
        }
        String[][] result = new String[rows.size()][];
        for (int i = 0; i < rows.size(); i++) {
            result[i] = (String[]) rows.get(i);
        }
        return result;
    }

    // Structural checksum: rows*10000 + cols*1000 + sum of all cell bytes.
    static int checksum(String[][] rows) {
        int cols = rows.length > 0 ? rows[0].length : 0;
        int sum = 0;
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < rows[r].length; c++) {
                String cell = rows[r][c];
                if (cell != null) {
                    for (int i = 0; i < cell.length(); i++) {
                        sum += (cell.charAt(i) & 0xff);
                    }
                }
            }
        }
        return rows.length * 10000 + cols * 1000 + sum;
    }

    // ---- mock backend returning a 3-row, 2-column result set ----
    static void writeRowDesc(OutputStream out, String[] names, int[] typeOids) throws Exception {
        // size: Int16 count + per field (name+NUL + 4 + 2 + 4 + 2 + 4 + 2)
        int size = 2;
        for (int i = 0; i < names.length; i++) {
            size += names[i].length() + 1 + 18;
        }
        byte[] rd = new byte[size];
        int p = 0;
        rd[p++] = (byte) (names.length >>> 8);
        rd[p++] = (byte) names.length;
        for (int f = 0; f < names.length; f++) {
            byte[] nm = PgQuery.ascii(names[f]);
            System.arraycopy(nm, 0, rd, p, nm.length);
            p += nm.length;
            rd[p++] = 0; // name NUL
            PgQuery.putInt32(rd, p, 0);
            p += 4; // table OID
            rd[p++] = 0;
            rd[p++] = 0; // column attr
            PgQuery.putInt32(rd, p, typeOids[f]);
            p += 4; // type OID
            int tlen = (typeOids[f] == 23) ? 4 : -1; // Int16 type len (int4=4, text=var)
            rd[p++] = (byte) (tlen >>> 8);
            rd[p++] = (byte) tlen;
            PgQuery.putInt32(rd, p, -1);
            p += 4; // type mod
            rd[p++] = 0;
            rd[p++] = 0; // format text
        }
        PgQuery.writeMsg(out, 'T', rd);
    }

    static void writeDataRow(OutputStream out, String[] vals) throws Exception {
        int size = 2;
        for (int i = 0; i < vals.length; i++) {
            size += 4 + vals[i].length();
        }
        byte[] dr = new byte[size];
        int p = 0;
        dr[p++] = (byte) (vals.length >>> 8);
        dr[p++] = (byte) vals.length;
        for (int i = 0; i < vals.length; i++) {
            byte[] v = PgQuery.ascii(vals[i]);
            PgQuery.putInt32(dr, p, v.length);
            p += 4;
            System.arraycopy(v, 0, dr, p, v.length);
            p += v.length;
        }
        PgQuery.writeMsg(out, 'D', dr);
    }

    static final class MockServer extends Thread {
        final ServerSocket ss;
        volatile int err;

        MockServer(ServerSocket ss) {
            this.ss = ss;
        }

        public void run() {
            try {
                Socket c = ss.accept();
                InputStream in = c.getInputStream();
                OutputStream out = c.getOutputStream();

                // consume StartupMessage
                byte[] lb = new byte[4];
                PgQuery.readFully(in, lb, 0, 4);
                int slen = PgQuery.getInt32(lb, 0);
                byte[] body = new byte[slen - 4];
                PgQuery.readFully(in, body, 0, slen - 4);

                byte[] authOk = new byte[4];
                PgQuery.putInt32(authOk, 0, 0);
                PgQuery.writeMsg(out, 'R', authOk);
                PgQuery.writeMsg(out, 'Z', new byte[] { (byte) 'I' });

                // consume Query
                byte[] qh = new byte[5];
                PgQuery.readFully(in, qh, 0, 5);
                int qlen = PgQuery.getInt32(qh, 1);
                byte[] qbody = new byte[qlen - 4];
                PgQuery.readFully(in, qbody, 0, qlen - 4);

                // 2 columns: g int4(23), col text(25); 3 rows.
                writeRowDesc(out, new String[] { "g", "col" }, new int[] { 23, 25 });
                writeDataRow(out, new String[] { "1", "r1" });
                writeDataRow(out, new String[] { "2", "r2" });
                writeDataRow(out, new String[] { "3", "r3" });

                byte[] tag = PgQuery.ascii("SELECT 3");
                byte[] cc = new byte[tag.length + 1];
                System.arraycopy(tag, 0, cc, 0, tag.length);
                cc[tag.length] = 0;
                PgQuery.writeMsg(out, 'C', cc);
                PgQuery.writeMsg(out, 'Z', new byte[] { (byte) 'I' });

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
            MockServer server = new MockServer(ss);
            server.start();

            Socket s = new Socket("127.0.0.1", port);
            String[][] rows = query(s.getInputStream(), s.getOutputStream(), "test", "test", "SELECT g, 'r'||g FROM t");
            s.close();
            server.join();
            ss.close();
            if (server.err != 0) {
                return -2;
            }
            return checksum(rows); // 32642
        } catch (Exception e) {
            return -1;
        }
    }

    public static int real() {
        try {
            Socket s = new Socket("127.0.0.1", 5432);
            String[][] rows = query(s.getInputStream(), s.getOutputStream(), "postgres", "postgres",
                    "SELECT g, 'r' || g FROM generate_series(1,3) g ORDER BY g");
            s.close();
            return checksum(rows); // 32642
        } catch (Exception e) {
            return -1;
        }
    }
}
