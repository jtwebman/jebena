package java.time;

/**
 * Clean-room time-based amount of time: seconds plus a nanosecond adjustment
 * (0..999,999,999).
 */
public final class Duration implements Comparable<Duration> {

    public static final Duration ZERO = new Duration(0, 0);

    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final long SECONDS_PER_MINUTE = 60L;
    private static final long SECONDS_PER_HOUR = 3600L;
    private static final long SECONDS_PER_DAY = 86400L;

    private final long seconds;
    private final int nanos;

    private Duration(long seconds, int nanos) {
        this.seconds = seconds;
        this.nanos = nanos;
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

    private static Duration create(long seconds, int nanos) {
        if ((seconds | nanos) == 0) {
            return ZERO;
        }
        return new Duration(seconds, nanos);
    }

    public static Duration ofSeconds(long seconds) {
        return create(seconds, 0);
    }

    public static Duration ofMinutes(long minutes) {
        return create(minutes * SECONDS_PER_MINUTE, 0);
    }

    public static Duration ofHours(long hours) {
        return create(hours * SECONDS_PER_HOUR, 0);
    }

    public static Duration ofDays(long days) {
        return create(days * SECONDS_PER_DAY, 0);
    }

    public static Duration ofMillis(long millis) {
        long secs = floorDiv(millis, 1000);
        int mos = (int) floorMod(millis, 1000);
        return create(secs, mos * 1_000_000);
    }

    public long getSeconds() {
        return seconds;
    }

    public int getNano() {
        return nanos;
    }

    public long toMillis() {
        return seconds * 1000 + nanos / 1_000_000;
    }

    public long toMinutes() {
        return seconds / SECONDS_PER_MINUTE;
    }

    public long toHours() {
        return seconds / SECONDS_PER_HOUR;
    }

    public long toDays() {
        return seconds / SECONDS_PER_DAY;
    }

    public Duration plusSeconds(long secondsToAdd) {
        return plus(secondsToAdd, 0);
    }

    public Duration minusSeconds(long secondsToSubtract) {
        return plusSeconds(-secondsToSubtract);
    }

    public Duration plusMinutes(long minutesToAdd) {
        return plusSeconds(minutesToAdd * 60L);
    }

    public Duration plusHours(long hoursToAdd) {
        return plusSeconds(hoursToAdd * 3600L);
    }

    public Duration plusDays(long daysToAdd) {
        return plusSeconds(daysToAdd * 86400L);
    }

    public Duration minusMinutes(long minutesToSubtract) {
        return plusSeconds(-minutesToSubtract * 60L);
    }

    public Duration minusHours(long hoursToSubtract) {
        return plusSeconds(-hoursToSubtract * 3600L);
    }

    public Duration plus(Duration other) {
        return plus(other.seconds, other.nanos);
    }

    private Duration plus(long secondsToAdd, long nanosToAdd) {
        if ((secondsToAdd | nanosToAdd) == 0) {
            return this;
        }
        long epochSec = seconds + secondsToAdd;
        long totalNanos = nanos + nanosToAdd;
        epochSec += floorDiv(totalNanos, NANOS_PER_SECOND);
        int newNanos = (int) floorMod(totalNanos, NANOS_PER_SECOND);
        return create(epochSec, newNanos);
    }

    public int compareTo(Duration other) {
        int cmp = (seconds < other.seconds) ? -1 : (seconds > other.seconds ? 1 : 0);
        if (cmp != 0) {
            return cmp;
        }
        return nanos - other.nanos;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Duration) {
            Duration o = (Duration) obj;
            return seconds == o.seconds && nanos == o.nanos;
        }
        return false;
    }

    public int hashCode() {
        return (int) (seconds ^ (seconds >>> 32)) + (51 * nanos);
    }

    public String toString() {
        if (seconds == 0 && nanos == 0) {
            return "PT0S";
        }
        long effectiveTotalSecs = seconds;
        if (seconds < 0 && nanos > 0) {
            effectiveTotalSecs++;
        }
        long hours = effectiveTotalSecs / SECONDS_PER_HOUR;
        int minutes = (int) ((effectiveTotalSecs % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE);
        int secs = (int) (effectiveTotalSecs % SECONDS_PER_MINUTE);
        StringBuilder buf = new StringBuilder(24);
        buf.append("PT");
        if (hours != 0) {
            buf.append(hours).append('H');
        }
        if (minutes != 0) {
            buf.append(minutes).append('M');
        }
        if (secs == 0 && nanos == 0 && buf.length() > 2) {
            return buf.toString();
        }
        if (seconds < 0 && nanos > 0) {
            if (secs == 0) {
                buf.append("-0");
            } else {
                buf.append(secs);
            }
        } else {
            buf.append(secs);
        }
        if (nanos > 0) {
            long fracVal;
            if (seconds < 0) {
                fracVal = 2 * NANOS_PER_SECOND - nanos;
            } else {
                fracVal = nanos + NANOS_PER_SECOND;
            }
            // fracVal is a 10-digit number whose leading digit we drop, giving 9 fraction digits.
            String frac = Long.toString(fracVal).substring(1);
            int end = frac.length();
            while (end > 0 && frac.charAt(end - 1) == '0') {
                end--;
            }
            buf.append('.').append(frac.substring(0, end));
        }
        buf.append('S');
        return buf.toString();
    }
}
