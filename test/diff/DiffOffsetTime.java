import java.time.OffsetTime;
import java.time.LocalTime;
import java.time.ZoneOffset;

public class DiffOffsetTime {

    static int checksum(String s) {
        int sum = 0;
        for (int i = 0; i < s.length(); i++) {
            sum += s.charAt(i);
        }
        return sum;
    }

    public static int toStringChecksum() {
        OffsetTime t = OffsetTime.of(LocalTime.of(7, 4, 2), ZoneOffset.ofHours(2));
        return checksum(t.toString());
    }

    public static int utcEndsWithZ() {
        OffsetTime t = OffsetTime.of(LocalTime.of(9, 30, 0), ZoneOffset.UTC);
        String s = t.toString();
        return s.charAt(s.length() - 1) == 'Z' ? 1 : 0;
    }

    public static int utcChecksum() {
        OffsetTime t = OffsetTime.of(LocalTime.of(9, 30, 0), ZoneOffset.UTC);
        return checksum(t.toString());
    }

    public static int hourMinute() {
        OffsetTime t = OffsetTime.of(7, 4, 2, 0, ZoneOffset.ofHours(-5));
        return t.getHour() * 100 + t.getMinute();
    }

    public static int secondNano() {
        OffsetTime t = OffsetTime.of(7, 4, 2, 0, ZoneOffset.ofHours(-5));
        return t.getSecond() * 100 + t.getNano();
    }

    public static int offsetTotalSeconds() {
        OffsetTime t = OffsetTime.of(LocalTime.of(1, 2, 3), ZoneOffset.ofHoursMinutes(3, 30));
        return t.getOffset().getTotalSeconds();
    }

    public static int compareByInstant() {
        // 10:00+02:00 == instant 08:00Z ; 09:30Z == instant 09:30Z -> first earlier
        OffsetTime a = OffsetTime.of(LocalTime.of(10, 0, 0), ZoneOffset.ofHours(2));
        OffsetTime b = OffsetTime.of(LocalTime.of(9, 30, 0), ZoneOffset.UTC);
        int c = a.compareTo(b);
        return c < 0 ? -1 : (c > 0 ? 1 : 0);
    }

    public static int compareSameOffset() {
        OffsetTime a = OffsetTime.of(LocalTime.of(12, 0, 0), ZoneOffset.ofHours(1));
        OffsetTime b = OffsetTime.of(LocalTime.of(8, 0, 0), ZoneOffset.ofHours(1));
        int c = a.compareTo(b);
        return c < 0 ? -1 : (c > 0 ? 1 : 0);
    }

    public static int equalsCheck() {
        OffsetTime a = OffsetTime.of(LocalTime.of(6, 15, 0), ZoneOffset.ofHours(4));
        OffsetTime b = OffsetTime.of(6, 15, 0, 0, ZoneOffset.ofHours(4));
        return a.equals(b) ? 1 : 0;
    }

    public static int hashConsistent() {
        OffsetTime a = OffsetTime.of(LocalTime.of(6, 15, 0), ZoneOffset.ofHours(4));
        OffsetTime b = OffsetTime.of(6, 15, 0, 0, ZoneOffset.ofHours(4));
        return a.hashCode() == b.hashCode() ? 1 : 0;
    }
}
