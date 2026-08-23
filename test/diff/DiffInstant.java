import java.time.Instant;

public class DiffInstant {

    static int checksum(String s) {
        int cs = 0;
        for (int i = 0; i < s.length(); i++) {
            cs = cs * 31 + s.charAt(i);
        }
        return cs;
    }

    public static int parseEpochSec() {
        return (int) Instant.parse("2026-03-09T07:04:02Z").getEpochSecond();
    }

    public static int parseNano() {
        return Instant.parse("2026-03-09T07:04:02.250Z").getNano();
    }

    public static int ofEpochSecondNano() {
        return Instant.ofEpochSecond(100, 500000000).getNano();
    }

    public static int plusMillisSec() {
        return (int) Instant.ofEpochSecond(0, 0).plusMillis(1500).getEpochSecond();
    }

    public static int plusMillisNano() {
        return Instant.ofEpochSecond(0, 0).plusMillis(1500).getNano();
    }

    public static int plusNanosCarry() {
        Instant i = Instant.ofEpochSecond(1, 999999999).plusNanos(2);
        return (int) i.getEpochSecond() * 1000 + i.getNano() / 1000000;
    }

    public static int minusMillis() {
        Instant i = Instant.ofEpochSecond(10, 0).minusMillis(1500);
        return (int) i.getEpochSecond() * 1000000000 + i.getNano();
    }

    public static int toStringPlain() {
        return checksum(Instant.parse("2026-03-09T07:04:02Z").toString());
    }

    public static int toStringMillis() {
        return checksum(Instant.ofEpochSecond(1773082, 250000000).toString());
    }

    public static int toStringNanos() {
        return checksum(Instant.parse("2026-03-09T07:04:02.123456789Z").toString());
    }

    public static int roundTripEpoch() {
        return (int) Instant.ofEpochMilli(1741503842500L).toEpochMilli();
    }

    public static int isBeforeCheck() {
        Instant a = Instant.parse("2026-03-09T07:04:02Z");
        Instant b = Instant.parse("2026-03-09T07:04:02.250Z");
        return (a.isBefore(b) ? 1 : 0) * 10 + (b.isAfter(a) ? 1 : 0);
    }
}
