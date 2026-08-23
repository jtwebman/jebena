package java.time;

public final class OffsetDateTime {

    private static final int SECONDS_PER_DAY = 86400;

    private final LocalDateTime dateTime;
    private final ZoneOffset offset;

    private OffsetDateTime(LocalDateTime dateTime, ZoneOffset offset) {
        if (dateTime == null) {
            throw new NullPointerException("dateTime");
        }
        if (offset == null) {
            throw new NullPointerException("offset");
        }
        this.dateTime = dateTime;
        this.offset = offset;
    }

    public static OffsetDateTime of(LocalDateTime dateTime, ZoneOffset offset) {
        return new OffsetDateTime(dateTime, offset);
    }

    public static OffsetDateTime of(int year, int month, int dayOfMonth, int hour,
            int minute, int second, int nanoOfSecond, ZoneOffset offset) {
        LocalDateTime dt = LocalDateTime.of(year, month, dayOfMonth, hour, minute, second);
        return new OffsetDateTime(dt, offset);
    }

    public ZoneOffset getOffset() {
        return offset;
    }

    public LocalDateTime toLocalDateTime() {
        return dateTime;
    }

    public LocalDate toLocalDate() {
        return dateTime.toLocalDate();
    }

    public LocalTime toLocalTime() {
        return dateTime.toLocalTime();
    }

    public int getYear() {
        return dateTime.getYear();
    }

    public int getMonthValue() {
        return dateTime.getMonthValue();
    }

    public int getDayOfMonth() {
        return dateTime.getDayOfMonth();
    }

    public int getHour() {
        return dateTime.getHour();
    }

    public int getMinute() {
        return dateTime.getMinute();
    }

    public int getSecond() {
        return dateTime.getSecond();
    }

    public int getNano() {
        return dateTime.toLocalTime().getNano();
    }

    public long toEpochSecond() {
        long epochDay = dateTime.toLocalDate().toEpochDay();
        long secs = epochDay * SECONDS_PER_DAY + dateTime.toLocalTime().toSecondOfDay();
        secs -= offset.getTotalSeconds();
        return secs;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof OffsetDateTime)) {
            return false;
        }
        OffsetDateTime other = (OffsetDateTime) obj;
        return dateTime.equals(other.dateTime) && offset.equals(other.offset);
    }

    public int hashCode() {
        return dateTime.hashCode() ^ offset.hashCode();
    }

    public String toString() {
        return dateTime.toString() + offset.getId();
    }
}
