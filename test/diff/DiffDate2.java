import java.time.LocalDate;

/**
 * Proves the JDK-faithful LocalDate signatures resolve against jbase from real-javac
 * bytecode: compareTo/isBefore/isAfter/isEqual take java.time.chrono.ChronoLocalDate and
 * getDayOfWeek() returns java.time.DayOfWeek (previously jbase used LocalDate/int, causing
 * MethodNotFound). Every method returns a deterministic int checked byte-for-byte vs real java.
 */
public class DiffDate2 {

    static final LocalDate A = LocalDate.of(2026, 1, 15);
    static final LocalDate B = LocalDate.of(2026, 11, 28);

    public static int cmpSign() {
        return Integer.signum(A.compareTo(B)); // -1
    }

    public static int cmpEqual() {
        return Integer.signum(A.compareTo(A)); // 0
    }

    public static int beforeAfter() {
        int a = A.isBefore(B) ? 1 : 0;
        int b = B.isAfter(A) ? 1 : 0;
        int c = B.isBefore(A) ? 1 : 0;
        return a * 100 + b * 10 + c; // 110
    }

    public static int isEqualCase() {
        return (A.isEqual(A) ? 1 : 0) * 10 + (A.isEqual(B) ? 1 : 0); // 10
    }

    public static int dowThursday() {
        return A.getDayOfWeek().getValue(); // 2026-01-15 is Thursday -> 4
    }

    public static int dowSunday() {
        return LocalDate.of(2026, 1, 18).getDayOfWeek().getValue(); // Sunday -> 7
    }

    public static int untilDays() {
        return A.until(B).getDays(); // 0y10m13d -> 13
    }

    public static int untilMonths() {
        return A.until(B).getMonths(); // 10
    }
}
