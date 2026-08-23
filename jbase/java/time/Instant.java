package java.time;

/**
 * Clean-room instant on the time-line, measured as seconds since the epoch
 * 1970-01-01T00:00:00Z plus a nanosecond-of-second in the range 0..999,999,999.
 */
public final class Instant implements Comparable<Instant> {

    public static final Instant EPOCH = new Instant(0, 0);

    private static final long NANOS_PER_SECOND = 1000000000L;
    private static final long DAYS_0000_TO_1970 = (146097L * 5L) - (30L * 365L + 7L);
    private static final long SECONDS_PER_DAY = 86400L;

    private final long epochSecond;
    private final int nano;

    private Instant(long epochSecond, int nano) {
        this.epochSecond = epochSecond;
        this.nano = nano;
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

    private static Instant create(long seconds, int nanoOfSecond) {
        if ((seconds | nanoOfSecond) == 0) {
            return EPOCH;
        }
        return new Instant(seconds, nanoOfSecond);
    }

    public static Instant ofEpochSecond(long epochSecond) {
        return create(epochSecond, 0);
    }

    public static Instant ofEpochSecond(long epochSecond, long nanoAdjustment) {
        long secs = epochSecond + floorDiv(nanoAdjustment, NANOS_PER_SECOND);
        int nos = (int) floorMod(nanoAdjustment, NANOS_PER_SECOND);
        return create(secs, nos);
    }

    public static Instant ofEpochMilli(long epochMilli) {
        long secs = floorDiv(epochMilli, 1000);
        int mos = (int) floorMod(epochMilli, 1000);
        return create(secs, mos * 1000000);
    }

    public long getEpochSecond() {
        return epochSecond;
    }

    public int getNano() {
        return nano;
    }

    public long toEpochMilli() {
        if (epochSecond < 0 && nano > 0) {
            long millis = (epochSecond + 1) * 1000;
            long adjustment = nano / 1000000 - 1000;
            return millis + adjustment;
        }
        return epochSecond * 1000 + nano / 1000000;
    }

    private Instant plus(long secondsToAdd, long nanosToAdd) {
        if ((secondsToAdd | nanosToAdd) == 0) {
            return this;
        }
        long epochSec = epochSecond + secondsToAdd;
        epochSec = epochSec + nanosToAdd / NANOS_PER_SECOND;
        nanosToAdd = nanosToAdd % NANOS_PER_SECOND;
        long nanoAdjustment = nano + nanosToAdd;
        return ofEpochSecond(epochSec, nanoAdjustment);
    }

    public Instant plusSeconds(long secondsToAdd) {
        return plus(secondsToAdd, 0);
    }

    public Instant minusSeconds(long secondsToSubtract) {
        return plus(-secondsToSubtract, 0);
    }

    public Instant plusMillis(long millisToAdd) {
        return plus(millisToAdd / 1000, (millisToAdd % 1000) * 1000000);
    }

    public Instant minusMillis(long millisToSubtract) {
        return plus(-(millisToSubtract / 1000), -(millisToSubtract % 1000) * 1000000);
    }

    public Instant plusNanos(long nanosToAdd) {
        return plus(0, nanosToAdd);
    }

    public Instant minusNanos(long nanosToSubtract) {
        return plus(0, -nanosToSubtract);
    }

    public int compareTo(Instant other) {
        if (epochSecond < other.epochSecond) {
            return -1;
        }
        if (epochSecond > other.epochSecond) {
            return 1;
        }
        if (nano < other.nano) {
            return -1;
        }
        if (nano > other.nano) {
            return 1;
        }
        return 0;
    }

    public boolean isBefore(Instant other) {
        return compareTo(other) < 0;
    }

    public boolean isAfter(Instant other) {
        return compareTo(other) > 0;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Instant) {
            Instant o = (Instant) obj;
            return epochSecond == o.epochSecond && nano == o.nano;
        }
        return false;
    }

    public int hashCode() {
        return (int) (epochSecond ^ (epochSecond >>> 32)) + 51 * nano;
    }

    // -------------------------------------------------------------------------
    // Parsing of ISO-8601, e.g. "2026-03-09T07:04:02Z" or "...T07:04:02.250Z"

    public static Instant parse(CharSequence text) {
        String s = text.toString();
        int tPos = s.indexOf('T');
        String datePart = s.substring(0, tPos);
        String timePart = s.substring(tPos + 1);
        if (timePart.length() > 0
                && (timePart.charAt(timePart.length() - 1) == 'Z'
                    || timePart.charAt(timePart.length() - 1) == 'z')) {
            timePart = timePart.substring(0, timePart.length() - 1);
        }

        // date: [-]YYYY-MM-DD
        boolean negYear = false;
        String dp = datePart;
        if (dp.length() > 0 && dp.charAt(0) == '-') {
            negYear = true;
            dp = dp.substring(1);
        } else if (dp.length() > 0 && dp.charAt(0) == '+') {
            dp = dp.substring(1);
        }
        int d1 = dp.indexOf('-');
        int d2 = dp.indexOf("-", d1 + 1);
        int year = Integer.parseInt(dp.substring(0, d1));
        if (negYear) {
            year = -year;
        }
        int month = Integer.parseInt(dp.substring(d1 + 1, d2));
        int day = Integer.parseInt(dp.substring(d2 + 1));

        // time: HH:MM:SS[.fraction]
        int fracDot = timePart.indexOf('.');
        String hms = fracDot < 0 ? timePart : timePart.substring(0, fracDot);
        int nanoVal = 0;
        if (fracDot >= 0) {
            String frac = timePart.substring(fracDot + 1);
            StringBuilder sb = new StringBuilder(frac);
            while (sb.length() < 9) {
                sb.append('0');
            }
            if (sb.length() > 9) {
                sb.setLength(9);
            }
            nanoVal = Integer.parseInt(sb.toString());
        }
        int c1 = hms.indexOf(':');
        int c2 = hms.indexOf(":", c1 + 1);
        int hour = Integer.parseInt(hms.substring(0, c1));
        int minute = Integer.parseInt(hms.substring(c1 + 1, c2 < 0 ? hms.length() : c2));
        int second = c2 < 0 ? 0 : Integer.parseInt(hms.substring(c2 + 1));

        long epochDay = toEpochDay(year, month, day);
        long secs = epochDay * SECONDS_PER_DAY
                + hour * 3600L + minute * 60L + second;
        return create(secs, nanoVal);
    }

    private static boolean isLeapYear(long year) {
        return ((year & 3) == 0) && ((year % 100) != 0 || (year % 400) == 0);
    }

    private static long toEpochDay(int year, int month, int day) {
        long y = year;
        long m = month;
        long total = 0;
        total += 365 * y;
        if (y >= 0) {
            total += (y + 3) / 4 - (y + 99) / 100 + (y + 399) / 400;
        } else {
            total -= y / -4 - y / -100 + y / -400;
        }
        total += (367 * m - 362) / 12;
        total += day - 1;
        if (m > 2) {
            total--;
            if (!isLeapYear(year)) {
                total--;
            }
        }
        return total - DAYS_0000_TO_1970;
    }

    // -------------------------------------------------------------------------

    private static void pad(StringBuilder buf, int value, int width) {
        String s = Integer.toString(value);
        for (int i = s.length(); i < width; i++) {
            buf.append('0');
        }
        buf.append(s);
    }

    public String toString() {
        long epochDay = floorDiv(epochSecond, SECONDS_PER_DAY);
        int secsOfDay = (int) floorMod(epochSecond, SECONDS_PER_DAY);

        long zeroDay = epochDay + DAYS_0000_TO_1970;
        zeroDay -= 60;
        long adjust = 0;
        if (zeroDay < 0) {
            long adjustCycles = (zeroDay + 1) / 146097 - 1;
            adjust = adjustCycles * 400;
            zeroDay += -adjustCycles * 146097;
        }
        long yearEst = (400 * zeroDay + 591) / 146097;
        long doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400);
        if (doyEst < 0) {
            yearEst--;
            doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400);
        }
        yearEst += adjust;
        int marchDoy0 = (int) doyEst;
        int marchMonth0 = (marchDoy0 * 5 + 2) / 153;
        int month = (marchMonth0 + 2) % 12 + 1;
        int dom = marchDoy0 - (marchMonth0 * 306 + 5) / 10 + 1;
        yearEst += marchMonth0 / 10;
        int year = (int) yearEst;

        int hour = secsOfDay / 3600;
        int minute = (secsOfDay / 60) % 60;
        int second = secsOfDay % 60;

        StringBuilder buf = new StringBuilder();
        if (year > 9999) {
            buf.append('+');
            buf.append(year);
        } else if (year < 0) {
            if (year <= -1000) {
                buf.append(year);
            } else {
                buf.append('-');
                pad(buf, -year, 4);
            }
        } else {
            pad(buf, year, 4);
        }
        buf.append('-');
        pad(buf, month, 2);
        buf.append('-');
        pad(buf, dom, 2);
        buf.append('T');
        pad(buf, hour, 2);
        buf.append(':');
        pad(buf, minute, 2);
        buf.append(':');
        pad(buf, second, 2);
        if (nano != 0) {
            buf.append('.');
            if (nano % 1000000 == 0) {
                pad(buf, nano / 1000000, 3);
            } else if (nano % 1000 == 0) {
                pad(buf, nano / 1000, 6);
            } else {
                pad(buf, nano, 9);
            }
        }
        buf.append('Z');
        return buf.toString();
    }
}
