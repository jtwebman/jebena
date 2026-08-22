package java.time;

/**
 * Clean-room immutable date-time pairing a {@link LocalDate} and {@link LocalTime}.
 */
public final class LocalDateTime implements Comparable<LocalDateTime> {

    private static final int SECONDS_PER_DAY = 86400;

    private final LocalDate date;
    private final LocalTime time;

    private LocalDateTime(LocalDate date, LocalTime time) {
        this.date = date;
        this.time = time;
    }

    private static long floorDiv(long x, long y) {
        long r = x / y;
        if ((x ^ y) < 0 && r * y != x) {
            r--;
        }
        return r;
    }

    private static long floorMod(long x, long y) {
        return x - floorDiv(x, y) * y;
    }

    public static LocalDateTime of(LocalDate date, LocalTime time) {
        return new LocalDateTime(date, time);
    }

    public static LocalDateTime of(int year, int month, int day, int hour, int minute) {
        return new LocalDateTime(LocalDate.of(year, month, day), LocalTime.of(hour, minute));
    }

    public static LocalDateTime of(int year, int month, int day, int hour, int minute, int second) {
        return new LocalDateTime(LocalDate.of(year, month, day), LocalTime.of(hour, minute, second));
    }

    public LocalDate toLocalDate() {
        return date;
    }

    public LocalTime toLocalTime() {
        return time;
    }

    public int getYear() {
        return date.getYear();
    }

    public int getMonthValue() {
        return date.getMonthValue();
    }

    public int getDayOfMonth() {
        return date.getDayOfMonth();
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

    public LocalDateTime plusDays(long days) {
        return with(date.plusDays(days), time);
    }

    public LocalDateTime minusDays(long days) {
        return plusDays(-days);
    }

    public LocalDateTime plusHours(long hours) {
        return plusSeconds(hours * 3600);
    }

    public LocalDateTime plusMinutes(long minutes) {
        return plusSeconds(minutes * 60);
    }

    public LocalDateTime plusSeconds(long secondsToAdd) {
        if (secondsToAdd == 0) {
            return this;
        }
        long total = time.toSecondOfDay() + secondsToAdd;
        long daysOverflow = floorDiv(total, SECONDS_PER_DAY);
        int newSofd = (int) floorMod(total, SECONDS_PER_DAY);
        LocalDate newDate = date.plusDays(daysOverflow);
        LocalTime newTime = (newSofd == time.toSecondOfDay()) ? time : LocalTime.ofSecondOfDay(newSofd);
        return with(newDate, newTime);
    }

    public LocalDateTime minusHours(long hours) {
        return plusHours(-hours);
    }

    public LocalDateTime minusMinutes(long minutes) {
        return plusMinutes(-minutes);
    }

    public LocalDateTime minusSeconds(long seconds) {
        return plusSeconds(-seconds);
    }

    private LocalDateTime with(LocalDate newDate, LocalTime newTime) {
        if (date == newDate && time == newTime) {
            return this;
        }
        return new LocalDateTime(newDate, newTime);
    }

    public int compareTo(LocalDateTime other) {
        int cmp = date.compareTo(other.date);
        if (cmp == 0) {
            cmp = time.compareTo(other.time);
        }
        return cmp;
    }

    public boolean isBefore(LocalDateTime other) {
        return compareTo(other) < 0;
    }

    public boolean isAfter(LocalDateTime other) {
        return compareTo(other) > 0;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof LocalDateTime) {
            LocalDateTime o = (LocalDateTime) obj;
            return date.equals(o.date) && time.equals(o.time);
        }
        return false;
    }

    public int hashCode() {
        return date.hashCode() ^ time.hashCode();
    }

    public String toString() {
        return date.toString() + "T" + time.toString();
    }
}
