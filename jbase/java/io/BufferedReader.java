package java.io;

/**
 * Clean-room java.io.BufferedReader. Wraps another Reader and adds line-oriented
 * reading. readLine() consumes up to a '\n' (or EOF), drops a single trailing
 * '\r' if present, and returns null once nothing more can be read. The bulk read
 * and close simply delegate to the wrapped reader.
 */
public class BufferedReader extends Reader {

    private final Reader in;

    public BufferedReader(Reader in) {
        this.in = in;
    }

    public int read(char[] cbuf, int off, int len) throws IOException {
        return in.read(cbuf, off, len);
    }

    public int read() throws IOException {
        return in.read();
    }

    public String readLine() throws IOException {
        StringBuilder sb = new StringBuilder();
        int c = in.read();
        if (c < 0) {
            return null;
        }
        while (c >= 0) {
            if (c == '\n') {
                break;
            }
            sb.append((char) c);
            c = in.read();
        }
        // Strip a single trailing '\r' left before the newline (CRLF endings).
        int len = sb.length();
        if (len > 0 && sb.charAt(len - 1) == '\r') {
            sb.setLength(len - 1);
        }
        return sb.toString();
    }

    public void close() throws IOException {
        in.close();
    }
}
