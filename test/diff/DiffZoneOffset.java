import java.time.ZoneOffset;

public class DiffZoneOffset {

    private static int checksum(String s) {
        int acc = 0;
        for (int i = 0; i < s.length(); i++) {
            acc = acc * 31 + s.charAt(i);
        }
        return acc;
    }

    public static int utcId() {
        return checksum(ZoneOffset.UTC.getId());
    }

    public static int ofHoursPos() {
        return checksum(ZoneOffset.ofHours(5).getId());
    }

    public static int ofHoursNeg() {
        return checksum(ZoneOffset.ofHours(-8).getId());
    }

    public static int ofHoursMinutes() {
        return checksum(ZoneOffset.ofHoursMinutes(5, 30).getId());
    }

    public static int ofHoursMinutesNeg() {
        return checksum(ZoneOffset.ofHoursMinutes(-4, -30).getId());
    }

    public static int totalSecondsId() {
        return checksum(ZoneOffset.ofTotalSeconds(3661).getId());
    }

    public static int negSecondsId() {
        return checksum(ZoneOffset.ofTotalSeconds(-3661).getId());
    }

    public static int totalSecondsValue() {
        return ZoneOffset.ofHoursMinutes(5, 30).getTotalSeconds();
    }

    public static int utcTotal() {
        return ZoneOffset.UTC.getTotalSeconds();
    }

    public static int maxOffset() {
        return checksum(ZoneOffset.ofHours(18).getId());
    }

    public static int equalsCheck() {
        ZoneOffset a = ZoneOffset.ofTotalSeconds(19800);
        ZoneOffset b = ZoneOffset.ofHoursMinutes(5, 30);
        return (a.equals(b) && a.hashCode() == b.hashCode()) ? 1 : 0;
    }
}
