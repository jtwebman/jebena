package java.io;

/**
 * Clean-room java.io.PrintWriter. Wraps another Writer and adds text-formatting
 * conveniences: print/println of every primitive plus String/Object, printf via
 * String.format, and the raw Writer overrides delegating to the wrapped sink.
 * Like the platform class the print/println/printf methods do not propagate
 * IOException; the in-memory writers never throw anyway.
 */
public class PrintWriter extends Writer {

    private final Writer out;

    public PrintWriter(Writer out) {
        this.out = out;
    }

    // ---- raw Writer overrides: delegate straight to the wrapped writer ----

    public void write(char[] cbuf, int off, int len) throws IOException {
        out.write(cbuf, off, len);
    }

    public void flush() throws IOException {
        out.flush();
    }

    public void close() throws IOException {
        out.close();
    }

    // ---- print: write the text form, swallowing IOException ----

    public void print(String s) {
        try {
            out.write((s == null) ? "null" : s);
        } catch (IOException e) {
            // in-memory writers never throw; match PrintWriter's quiet contract
        }
    }

    public void print(Object o) {
        print(String.valueOf(o));
    }

    public void print(boolean b) {
        print(b ? "true" : "false");
    }

    public void print(char c) {
        print(String.valueOf(c));
    }

    public void print(int i) {
        print(String.valueOf(i));
    }

    public void print(long l) {
        print(String.valueOf(l));
    }

    public void print(double d) {
        print(String.valueOf(d));
    }

    public void print(char[] s) {
        print(new String(s));
    }

    // ---- println: print, then a newline ----

    public void println() {
        print("\n");
    }

    public void println(String s) {
        print(s);
        print("\n");
    }

    public void println(Object o) {
        println(String.valueOf(o));
    }

    public void println(boolean b) {
        println(b ? "true" : "false");
    }

    public void println(char c) {
        println(String.valueOf(c));
    }

    public void println(int i) {
        println(String.valueOf(i));
    }

    public void println(long l) {
        println(String.valueOf(l));
    }

    public void println(double d) {
        println(String.valueOf(d));
    }

    public void println(char[] s) {
        println(new String(s));
    }

    // ---- printf: format via String.format, then write ----

    public PrintWriter printf(String format, Object... args) {
        print(String.format(format, args));
        return this;
    }

    public PrintWriter format(String format, Object... args) {
        print(String.format(format, args));
        return this;
    }
}
