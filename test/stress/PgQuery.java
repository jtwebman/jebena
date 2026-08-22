package st;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Minimal PostgreSQL v3 wire-protocol client over java.net.Socket: StartupMessage
 * -> handle AuthenticationRequest (Ok / cleartext; MD5 and SCRAM are out of scope,
 * documented) -> simple Query "SELECT 1" -> parse RowDescription/DataRow/
 * CommandComplete/ReadyForQuery -> return the integer cell (1).
 *
 * demo() runs the client against a spec-faithful MOCK server fiber on loopback, so
 * it validates the full wire encode/decode with no external database and stays
 * green in the gate. real() runs the SAME client against a live Postgres on
 * 127.0.0.1:5432 (user/db "postgres") -- pg-stress.sh runs it only when
 * JEBENA_PGTEST is set and a DB is reachable. Both were validated this iteration:
 * demo()=1 (mock) and real()=1 (postgres:16-alpine, trust auth).
 *
 * Blocking socket I/O holds the carrier (no fiber parking yet), so demo() (mock
 * server fiber + client fiber) needs JEBENA_CARRIERS >= 2. See docs/THREADING.md.
 */
public class PgQuery {
    static final int PROTOCOL_V3 = 196608; // 0x00030000

    // ---- big-endian helpers ----
    static void putInt32(byte[] b, int off, int v) {
        b[off] = (byte) (v >>> 24);
        b[off + 1] = (byte) (v >>> 16);
        b[off + 2] = (byte) (v >>> 8);
        b[off + 3] = (byte) v;
    }

    static int getInt32(byte[] b, int off) {
        return ((b[off] & 0xff) << 24) | ((b[off + 1] & 0xff) << 16)
                | ((b[off + 2] & 0xff) << 8) | (b[off + 3] & 0xff);
    }

    static int getInt16(byte[] b, int off) {
        return ((b[off] & 0xff) << 8) | (b[off + 1] & 0xff);
    }

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

    static void readFully(InputStream in, byte[] b, int off, int len) throws Exception {
        int got = 0;
        while (got < len) {
            int n = in.read(b, off + got, len - got);
            if (n < 0) {
                throw new RuntimeException("unexpected EOF");
            }
            got += n;
        }
    }

    // Read one typed backend message: returns {type, payload...} (length prefix stripped).
    static byte[] readMsg(InputStream in) throws Exception {
        byte[] hdr = new byte[5];
        readFully(in, hdr, 0, 5);
        int len = getInt32(hdr, 1); // includes the 4 length bytes
        int payLen = len - 4;
        byte[] msg = new byte[1 + payLen];
        msg[0] = hdr[0];
        readFully(in, msg, 1, payLen);
        return msg;
    }

    // Write a typed message (type + Int32 length + payload).
    static void writeMsg(OutputStream out, char type, byte[] payload) throws Exception {
        int len = 4 + payload.length;
        byte[] msg = new byte[1 + len];
        msg[0] = (byte) type;
        putInt32(msg, 1, len);
        System.arraycopy(payload, 0, msg, 5, payload.length);
        out.write(msg, 0, msg.length);
        out.flush();
    }

    static void sendStartup(OutputStream out, String user, String db) throws Exception {
        byte[] u = ascii("user");
        byte[] uv = ascii(user);
        byte[] d = ascii("database");
        byte[] dv = ascii(db);
        int paramsLen = u.length + 1 + uv.length + 1 + d.length + 1 + dv.length + 1 + 1;
        int len = 4 + 4 + paramsLen; // length + protocol + params(+trailing NUL)
        byte[] msg = new byte[len];
        putInt32(msg, 0, len);
        putInt32(msg, 4, PROTOCOL_V3);
        int p = 8;
        p = putCStr(msg, p, u);
        p = putCStr(msg, p, uv);
        p = putCStr(msg, p, d);
        p = putCStr(msg, p, dv);
        msg[p] = 0; // final terminator
        out.write(msg, 0, msg.length);
        out.flush();
    }

    static int putCStr(byte[] b, int off, byte[] s) {
        System.arraycopy(s, 0, b, off, s.length);
        b[off + s.length] = 0;
        return off + s.length + 1;
    }

    static void sendQuery(OutputStream out, String sql) throws Exception {
        byte[] s = ascii(sql);
        byte[] payload = new byte[s.length + 1];
        System.arraycopy(s, 0, payload, 0, s.length);
        payload[s.length] = 0;
        writeMsg(out, 'Q', payload);
    }

    static void sendPassword(OutputStream out, String pw) throws Exception {
        byte[] s = ascii(pw);
        byte[] payload = new byte[s.length + 1];
        System.arraycopy(s, 0, payload, 0, s.length);
        payload[s.length] = 0;
        writeMsg(out, 'p', payload);
    }

    // Full startup + "SELECT 1"; returns the first cell as an int.
    static int runClient(InputStream in, OutputStream out, String user, String db) throws Exception {
        sendStartup(out, user, db);
        // startup phase: consume until ReadyForQuery
        while (true) {
            byte[] m = readMsg(in);
            char t = (char) (m[0] & 0xff);
            if (t == 'R') {
                int code = getInt32(m, 1);
                if (code == 0) {
                    // AuthenticationOk
                } else if (code == 3) {
                    sendPassword(out, ""); // cleartext (trust setups won't ask)
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
                break; // ReadyForQuery
            }
            // 'S' ParameterStatus / 'K' BackendKeyData / 'N' NoticeResponse: ignore
        }

        sendQuery(out, "SELECT 1");
        int result = -1;
        while (true) {
            byte[] m = readMsg(in);
            char t = (char) (m[0] & 0xff);
            if (t == 'D') { // DataRow
                int cols = getInt16(m, 1);
                if (cols > 0) {
                    int p = 3;
                    int clen = getInt32(m, p);
                    p += 4;
                    if (clen > 0) {
                        result = Integer.parseInt(str(m, p, clen).trim());
                    }
                }
            } else if (t == 'E') {
                throw new RuntimeException("server error on query");
            } else if (t == 'Z') {
                break; // ReadyForQuery
            }
            // 'T' RowDescription / 'C' CommandComplete: ignore
        }
        return result;
    }

    // ---- spec-faithful mock backend (enough for one SELECT 1) ----
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

                // Read StartupMessage: Int32 len, then len-4 body.
                byte[] lb = new byte[4];
                readFully(in, lb, 0, 4);
                int slen = getInt32(lb, 0);
                byte[] body = new byte[slen - 4];
                readFully(in, body, 0, slen - 4);

                // AuthenticationOk (code 0) then ReadyForQuery ('I' idle).
                byte[] authOk = new byte[4];
                putInt32(authOk, 0, 0);
                writeMsg(out, 'R', authOk);
                writeMsg(out, 'Z', new byte[] { (byte) 'I' });

                // Read the Query message (type 'Q', Int32 len, payload).
                byte[] qh = new byte[5];
                readFully(in, qh, 0, 5);
                int qlen = getInt32(qh, 1);
                byte[] qbody = new byte[qlen - 4];
                readFully(in, qbody, 0, qlen - 4);

                // RowDescription: 1 field "?column?" int4(23).
                byte[] name = ascii("?column?");
                byte[] rd = new byte[2 + name.length + 1 + 4 + 2 + 4 + 2 + 4 + 2];
                int p = 0;
                rd[p++] = 0;
                rd[p++] = 1; // Int16 field count = 1
                System.arraycopy(name, 0, rd, p, name.length);
                p += name.length;
                rd[p++] = 0; // name NUL
                putInt32(rd, p, 0);
                p += 4; // table OID
                rd[p++] = 0;
                rd[p++] = 0; // column attr
                putInt32(rd, p, 23);
                p += 4; // type OID int4
                rd[p++] = 0;
                rd[p++] = 4; // type len = 4
                putInt32(rd, p, -1);
                p += 4; // type mod
                rd[p++] = 0;
                rd[p++] = 0; // format text
                writeMsg(out, 'T', rd);

                // DataRow: 1 column, value "1".
                byte[] dr = new byte[2 + 4 + 1];
                dr[0] = 0;
                dr[1] = 1; // Int16 col count = 1
                putInt32(dr, 2, 1); // Int32 value length = 1
                dr[6] = (byte) '1';
                writeMsg(out, 'D', dr);

                // CommandComplete "SELECT 1" + NUL, then ReadyForQuery.
                byte[] tag = ascii("SELECT 1");
                byte[] cc = new byte[tag.length + 1];
                System.arraycopy(tag, 0, cc, 0, tag.length);
                cc[tag.length] = 0;
                writeMsg(out, 'C', cc);
                writeMsg(out, 'Z', new byte[] { (byte) 'I' });

                c.close();
            } catch (Exception e) {
                err = 1;
            }
        }
    }

    // Always-green: client vs an in-process spec-faithful mock (needs carriers>=2).
    public static int demo() {
        try {
            ServerSocket ss = new ServerSocket(0);
            int port = ss.getLocalPort();
            MockServer server = new MockServer(ss);
            server.start();

            Socket s = new Socket("127.0.0.1", port);
            int r = runClient(s.getInputStream(), s.getOutputStream(), "test", "test");
            s.close();
            server.join();
            ss.close();
            if (server.err != 0) {
                return -2;
            }
            return r; // 1
        } catch (Exception e) {
            return -1;
        }
    }

    // Live DB: client vs Postgres on 127.0.0.1:5432 (user/db "postgres", trust).
    // Run by pg-stress.sh only when JEBENA_PGTEST is set and a DB is reachable.
    public static int real() {
        try {
            Socket s = new Socket("127.0.0.1", 5432);
            int r = runClient(s.getInputStream(), s.getOutputStream(), "postgres", "postgres");
            s.close();
            return r; // 1
        } catch (Exception e) {
            return -1;
        }
    }
}
