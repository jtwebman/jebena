/**
 * Differential coverage for programmatic stack-trace inspection (Throwable.
 * getStackTrace + StackTraceElement). Only the TOP user frames are examined —
 * frame[0..2] are the deterministic user methods c/b/a; deeper frames cross the
 * reflection/bootstrap boundary and legitimately differ from real java. Each
 * method returns a deterministic int checked byte-for-byte via breadth-diff.sh.
 */
public class DiffTrace {
    static void a() {
        b();
    }

    static void b() {
        c();
    }

    static void c() {
        throw new RuntimeException("trace");
    }

    private static StackTraceElement[] capture() {
        try {
            a();
        } catch (RuntimeException e) {
            return e.getStackTrace();
        }
        return new StackTraceElement[0];
    }

    static int topLine() {
        return capture()[0].getLineNumber(); // the `throw` line in c()
    }

    static int topMethod() {
        StackTraceElement[] t = capture();
        // encode method names of the top three frames: c/b/a -> lengths 1,1,1 + a check
        int acc = 0;
        acc = acc * 10 + t[0].getMethodName().length(); // "c"
        acc = acc * 10 + t[1].getMethodName().length(); // "b"
        acc = acc * 10 + t[2].getMethodName().length(); // "a"
        return acc; // 111
    }

    static int topClass() {
        StackTraceElement[] t = capture();
        return t[0].getClassName().equals("DiffTrace") ? 1 : 0;
    }

    static int lineOrder() {
        StackTraceElement[] t = capture();
        // c's throw line < b's call line < a's call line (source order), all > 0
        int l0 = t[0].getLineNumber();
        int l1 = t[1].getLineNumber();
        int l2 = t[2].getLineNumber();
        return (l0 < l1 && l1 < l2 && l0 > 0) ? (l2 - l0) : -1; // gap between a() and c()
    }

    static int fileName() {
        return capture()[0].getFileName().equals("DiffTrace.java") ? 1 : 0;
    }
}
