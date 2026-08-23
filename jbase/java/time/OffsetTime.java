package java.time;

/**
 * Clean-room immutable time-of-day with an offset from UTC, e.g. {@code 07:04:02+02:00}.
 *
 * Backed by {@link LocalTime} and {@link ZoneOffset}. Since jbase {@code LocalTime}
 * has no nanosecond storage, nanoseconds are always 0.
 */
public final class OffsetTime implements Comparable<OffsetTime> {

    private static final int SECONDS_PER_DAY = 86400;

    private final LocalTime time;
    private final ZoneOffset offset;

    private OffsetTime(LocalTime time, ZoneOffset offset) {
        if (time == null) {
            throw new NullPointerException("time");
        }
        if (offset == null) {
            throw new NullPointerException("offset");
        }
        this.time = time;
        this.offset = offset;
    }

    public static OffsetTime of(LocalTime time, ZoneOffset offset) {
        return new OffsetTime(time, offset);
    }

    public static OffsetTime of(int hour, int minute, int second, int nanoOfSecond,
            ZoneOffset offset) {
        LocalTime t = LocalTime.of(hour, minute, second);
        return new OffsetTime(t, offset);
    }

    public int getHour() {
        return time.getHour();
    }

    public int getMinute() {
        return time.getMinute();
    }

    public int getSecond() {
        return time.getSecond();
    }

    public int getNano() {
        return time.getNano();
    }

    public ZoneOffset getOffset() {
        return offset;
    }

    public LocalTime toLocalTime() {
        return time;
    }

    public int compareTo(OffsetTime other) {
        if (offset.equals(other.offset)) {
            return time.compareTo(other.time);
        }
        long thisSod = (long) time.toSecondOfDay() - offset.getTotalSeconds();
        long otherSod = (long) other.time.toSecondOfDay() - other.offset.getTotalSeconds();
        if (thisSod < otherSod) {
            return -1;
        }
        if (thisSod > otherSod) {
            return 1;
        }
        return time.compareTo(other.time);
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof OffsetTime)) {
            return false;
        }
        OffsetTime other = (OffsetTime) obj;
        return time.equals(other.time) && offset.equals(other.offset);
    }

    public int hashCode() {
        return time.hashCode() ^ offset.hashCode();
    }

    public String toString() {
        return time.toString() + offset.getId();
    }
}
