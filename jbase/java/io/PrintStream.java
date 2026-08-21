package java.io;

/**
 * Minimal clean-room java.io.PrintStream. print/println format via String.valueOf
 * and write the UTF-8 bytes to a VM file descriptor (native writeString).
 */
public class PrintStream {
    private final int fd;

    public PrintStream(int fd) {
        this.fd = fd;
    }

    public void print(String s) {
        writeString(fd, (s == null) ? "null" : s);
    }

    public void print(Object o) {
        writeString(fd, String.valueOf(o));
    }

    public void print(int i) {
        writeString(fd, String.valueOf(i));
    }

    public void print(long l) {
        writeString(fd, String.valueOf(l));
    }

    public void print(char c) {
        writeString(fd, String.valueOf(c));
    }

    public void print(boolean b) {
        writeString(fd, b ? "true" : "false");
    }

    public void print(double d) {
        writeString(fd, String.valueOf(d));
    }

    public void print(float f) {
        writeString(fd, String.valueOf(f));
    }

    public void print(char[] s) {
        writeString(fd, new String(s));
    }

    public void println() {
        writeString(fd, "\n");
    }

    public void println(String s) {
        print(s);
        writeString(fd, "\n");
    }

    public void println(Object o) {
        println(String.valueOf(o));
    }

    public void println(int i) {
        println(String.valueOf(i));
    }

    public void println(long l) {
        println(String.valueOf(l));
    }

    public void println(char c) {
        println(String.valueOf(c));
    }

    public void println(boolean b) {
        println(b ? "true" : "false");
    }

    public void println(double d) {
        println(String.valueOf(d));
    }

    public void println(float f) {
        println(String.valueOf(f));
    }

    public void println(char[] s) {
        println(new String(s));
    }

    public void flush() {}

    private static native void writeString(int fd, String s);
}
