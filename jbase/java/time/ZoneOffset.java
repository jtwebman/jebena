package java.time;

/**
 * Clean-room time-zone offset from UTC, e.g. {@code +02:00}.
 *
 * Pure computation, no tzdata. Range is limited to {@code -18:00} through
 * {@code +18:00} inclusive.
 */
public final class ZoneOffset extends ZoneId {

    private static final int MAX_SECONDS = 18 * 3600;

    public static final ZoneOffset UTC = new ZoneOffset(0);

    private final int totalSeconds;
    private final String id;

    private ZoneOffset(int totalSeconds) {
        this.totalSeconds = totalSeconds;
        this.id = buildId(totalSeconds);
    }

    private static String buildId(int totalSeconds) {
        if (totalSeconds == 0) {
            return "Z";
        }
        int abs = Math.abs(totalSeconds);
        int hours = abs / 3600;
        int minutes = (abs / 60) % 60;
        int seconds = abs % 60;
        StringBuilder sb = new StringBuilder();
        sb.append(totalSeconds < 0 ? "-" : "+");
        sb.append(hours < 10 ? "0" : "").append(hours);
        sb.append(minutes < 10 ? ":0" : ":").append(minutes);
        if (seconds != 0) {
            sb.append(seconds < 10 ? ":0" : ":").append(seconds);
        }
        return sb.toString();
    }

    public static ZoneOffset ofHours(int hours) {
        return ofHoursMinutesSeconds(hours, 0, 0);
    }

    public static ZoneOffset ofHoursMinutes(int hours, int minutes) {
        return ofHoursMinutesSeconds(hours, minutes, 0);
    }

    public static ZoneOffset ofHoursMinutesSeconds(int hours, int minutes, int seconds) {
        validate(hours, minutes, seconds);
        int total = hours * 3600 + minutes * 60 + seconds;
        return ofTotalSeconds(total);
    }

    private static void validate(int hours, int minutes, int seconds) {
        if (hours < -18 || hours > 18) {
            throw new IllegalArgumentException(
                    "Zone offset hours not in valid range: value " + hours
                            + " is not in the range -18 to 18");
        }
        if (hours > 0) {
            if (minutes < 0 || seconds < 0) {
                throw new IllegalArgumentException(
                        "Zone offset minutes and seconds must be positive because hours is positive");
            }
        } else if (hours < 0) {
            if (minutes > 0 || seconds > 0) {
                throw new IllegalArgumentException(
                        "Zone offset minutes and seconds must be negative because hours is negative");
            }
        } else if ((minutes > 0 && seconds < 0) || (minutes < 0 && seconds > 0)) {
            throw new IllegalArgumentException(
                    "Zone offset minutes and seconds must have the same sign");
        }
        if (minutes < -59 || minutes > 59) {
            throw new IllegalArgumentException(
                    "Zone offset minutes not in valid range: value " + minutes
                            + " is not in the range -59 to 59");
        }
        if (seconds < -59 || seconds > 59) {
            throw new IllegalArgumentException(
                    "Zone offset seconds not in valid range: value " + seconds
                            + " is not in the range -59 to 59");
        }
        if (Math.abs(hours) == 18 && (Math.abs(minutes) > 0 || Math.abs(seconds) > 0)) {
            throw new IllegalArgumentException(
                    "Zone offset not in valid range: -18:00 to +18:00");
        }
    }

    public static ZoneOffset ofTotalSeconds(int totalSeconds) {
        if (totalSeconds < -MAX_SECONDS || totalSeconds > MAX_SECONDS) {
            throw new IllegalArgumentException(
                    "Zone offset not in valid range: -18:00 to +18:00");
        }
        if (totalSeconds == 0) {
            return UTC;
        }
        return new ZoneOffset(totalSeconds);
    }

    public int getTotalSeconds() {
        return totalSeconds;
    }

    public String getId() {
        return id;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ZoneOffset)) {
            return false;
        }
        return totalSeconds == ((ZoneOffset) obj).totalSeconds;
    }

    public int hashCode() {
        return totalSeconds;
    }

    public String toString() {
        return id;
    }
}
