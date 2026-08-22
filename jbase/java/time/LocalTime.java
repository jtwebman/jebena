package java.time;

/**
 * Clean-room immutable time-of-day (hour, minute, second). Nanoseconds fixed at 0.
 */
public final class LocalTime implements Comparable<LocalTime> {

    private static final int SECONDS_PER_DAY = 86400;

    private final int hour;
    private final int minute;
    private final int second;
    private final int nano;

    private LocalTime(int hour, int minute, int second) {
        this.hour = hour;
        this.minute = minute;
        this.second = second;
        this.nano = 0;
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

    public static LocalTime of(int hour, int minute) {
        return of(hour, minute, 0);
    }

    public static LocalTime of(int hour, int minute, int second) {
        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException("Invalid value for hour: " + hour);
        }
        if (minute < 0 || minute > 59) {
            throw new IllegalArgumentException("Invalid value for minute: " + minute);
        }
        if (second < 0 || second > 59) {
            throw new IllegalArgumentException("Invalid value for second: " + second);
        }
        return new LocalTime(hour, minute, second);
    }

    public static LocalTime ofSecondOfDay(long secondOfDay) {
        if (secondOfDay < 0 || secondOfDay > SECONDS_PER_DAY - 1) {
            throw new IllegalArgumentException("Invalid value for secondOfDay: " + secondOfDay);
        }
        int secs = (int) secondOfDay;
        int hours = secs / 3600;
        secs -= hours * 3600;
        int minutes = secs / 60;
        secs -= minutes * 60;
        return new LocalTime(hours, minutes, secs);
    }

    public int getHour() {
        return hour;
    }

    public int getMinute() {
        return minute;
    }

    public int getSecond() {
        return second;
    }

    public int getNano() {
        return nano;
    }

    public int toSecondOfDay() {
        return hour * 3600 + minute * 60 + second;
    }

    public LocalTime plusHours(long hoursToAdd) {
        return plusSeconds(hoursToAdd * 3600);
    }

    public LocalTime plusMinutes(long minutesToAdd) {
        return plusSeconds(minutesToAdd * 60);
    }

    public LocalTime plusSeconds(long secondsToAdd) {
        if (secondsToAdd == 0) {
            return this;
        }
        long newSofd = floorMod(toSecondOfDay() + secondsToAdd, SECONDS_PER_DAY);
        return ofSecondOfDay(newSofd);
    }

    public LocalTime minusHours(long hoursToSubtract) {
        return plusHours(-hoursToSubtract);
    }

    public LocalTime minusMinutes(long minutesToSubtract) {
        return plusMinutes(-minutesToSubtract);
    }

    public LocalTime minusSeconds(long secondsToSubtract) {
        return plusSeconds(-secondsToSubtract);
    }

    public int compareTo(LocalTime other) {
        int cmp = hour - other.hour;
        if (cmp == 0) {
            cmp = minute - other.minute;
            if (cmp == 0) {
                cmp = second - other.second;
            }
        }
        return cmp;
    }

    public boolean isBefore(LocalTime other) {
        return compareTo(other) < 0;
    }

    public boolean isAfter(LocalTime other) {
        return compareTo(other) > 0;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof LocalTime) {
            LocalTime o = (LocalTime) obj;
            return hour == o.hour && minute == o.minute && second == o.second;
        }
        return false;
    }

    public int hashCode() {
        long total = toSecondOfDay();
        return (int) (total ^ (total >>> 32));
    }

    public String toString() {
        StringBuilder buf = new StringBuilder(8);
        buf.append(hour < 10 ? "0" : "").append(hour);
        buf.append(minute < 10 ? ":0" : ":").append(minute);
        if (second > 0) {
            buf.append(second < 10 ? ":0" : ":").append(second);
        }
        return buf.toString();
    }
}
