import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class DiffOdt {

    private static int csum(String s) {
        int h = 0;
        for (int i = 0; i < s.length(); i++) {
            h = h * 31 + s.charAt(i);
        }
        return h;
    }

    public static int toStringOffset() {
        OffsetDateTime o = OffsetDateTime.of(
                LocalDateTime.of(2026, 3, 9, 7, 4, 2), ZoneOffset.ofHours(2));
        return csum(o.toString());
    }

    public static int toStringUtc() {
        OffsetDateTime o = OffsetDateTime.of(
                LocalDateTime.of(2026, 3, 9, 7, 4, 2), ZoneOffset.UTC);
        String s = o.toString();
        return s.charAt(s.length() - 1) == 'Z' ? csum(s) : -1;
    }

    public static int toStringHms() {
        OffsetDateTime o = OffsetDateTime.of(2020, 12, 31, 23, 59, 58, 0,
                ZoneOffset.ofHoursMinutesSeconds(5, 30, 15));
        return csum(o.toString());
    }

    public static int offsetSeconds() {
        OffsetDateTime o = OffsetDateTime.of(
                LocalDateTime.of(2026, 3, 9, 7, 4, 2), ZoneOffset.ofHours(2));
        return o.getOffset().getTotalSeconds();
    }

    public static int epochSecondUtc() {
        OffsetDateTime o = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        return (int) o.toEpochSecond();
    }

    public static int epochSecondOffset() {
        OffsetDateTime o = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.ofHours(2));
        return (int) o.toEpochSecond();
    }

    public static int getters() {
        OffsetDateTime o = OffsetDateTime.of(
                LocalDateTime.of(2026, 3, 9, 7, 4, 2), ZoneOffset.ofHours(-5));
        return o.getHour() * 100 + o.getMinute();
    }

    public static int dateParts() {
        OffsetDateTime o = OffsetDateTime.of(2019, 7, 15, 8, 30, 45, 0, ZoneOffset.ofHours(3));
        return o.getYear() * 10000 + o.getMonthValue() * 100 + o.getDayOfMonth();
    }

    public static int equalsCase() {
        OffsetDateTime a = OffsetDateTime.of(
                LocalDateTime.of(2026, 3, 9, 7, 4, 2), ZoneOffset.ofHours(2));
        OffsetDateTime b = OffsetDateTime.of(2026, 3, 9, 7, 4, 2, 0, ZoneOffset.ofHours(2));
        OffsetDateTime c = OffsetDateTime.of(2026, 3, 9, 7, 4, 2, 0, ZoneOffset.ofHours(3));
        return (a.equals(b) ? 1 : 0) + (a.equals(c) ? 0 : 10) + (a.hashCode() == b.hashCode() ? 100 : 0);
    }

    public static int nanoZero() {
        OffsetDateTime o = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        return o.getNano();
    }
}
