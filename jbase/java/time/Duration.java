package java.time;

import java.math.BigInteger;

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
    private static final BigInteger BI_NANOS_PER_SECOND = BigInteger.valueOf(NANOS_PER_SECOND);

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

    public static Duration ofNanos(long nanos) {
        long secs = floorDiv(nanos, NANOS_PER_SECOND);
        int nos = (int) floorMod(nanos, NANOS_PER_SECOND);
        return create(secs, nos);
    }

    public static Duration ofSeconds(long seconds, long nanoAdjustment) {
        long secs = seconds + floorDiv(nanoAdjustment, NANOS_PER_SECOND);
        int nos = (int) floorMod(nanoAdjustment, NANOS_PER_SECOND);
        return create(secs, nos);
    }

    /**
     * Parse an ISO-8601 duration text of the form {@code PnDTnHnMnS}, with an
     * optional overall sign, optional per-field signs, and an optional
     * fractional part on the seconds field. Clean-room, hand-written parser.
     */
    public static Duration parse(CharSequence text) {
        String s = text.toString();
        int len = s.length();
        int idx = 0;
        boolean negate = false;
        if (idx < len) {
            char c = s.charAt(idx);
            if (c == '+') {
                idx++;
            } else if (c == '-') {
                negate = true;
                idx++;
            }
        }
        if (idx >= len || (s.charAt(idx) != 'P' && s.charAt(idx) != 'p')) {
            throw new IllegalArgumentException("Text cannot be parsed to a Duration: " + s);
        }
        idx++;
        long secondsAcc = 0;
        int nanoAcc = 0;
        boolean inTime = false;
        boolean parsedAny = false;
        while (idx < len) {
            char c = s.charAt(idx);
            if (c == 'T' || c == 't') {
                if (inTime) {
                    throw new IllegalArgumentException("Text cannot be parsed to a Duration: " + s);
                }
                inTime = true;
                idx++;
                continue;
            }
            boolean numNeg = false;
            if (c == '-') {
                numNeg = true;
                idx++;
            } else if (c == '+') {
                idx++;
            }
            int dstart = idx;
            while (idx < len && s.charAt(idx) >= '0' && s.charAt(idx) <= '9') {
                idx++;
            }
            if (idx == dstart) {
                throw new IllegalArgumentException("Text cannot be parsed to a Duration: " + s);
            }
            long value = Long.parseLong(s.substring(dstart, idx));
            int fracNanos = 0;
            boolean hasFraction = false;
            if (idx < len && (s.charAt(idx) == '.' || s.charAt(idx) == ',')) {
                idx++;
                int fstart = idx;
                while (idx < len && s.charAt(idx) >= '0' && s.charAt(idx) <= '9') {
                    idx++;
                }
                String frac = s.substring(fstart, idx);
                for (int i = 0; i < 9; i++) {
                    fracNanos *= 10;
                    if (i < frac.length()) {
                        fracNanos += frac.charAt(i) - '0';
                    }
                }
                hasFraction = true;
            }
            if (idx >= len) {
                throw new IllegalArgumentException("Text cannot be parsed to a Duration: " + s);
            }
            char unit = s.charAt(idx);
            idx++;
            if (numNeg) {
                value = -value;
                fracNanos = -fracNanos;
            }
            if ((unit == 'D' || unit == 'd') && !inTime) {
                if (hasFraction) {
                    throw new IllegalArgumentException("Text cannot be parsed to a Duration: " + s);
                }
                secondsAcc += value * SECONDS_PER_DAY;
            } else if ((unit == 'H' || unit == 'h') && inTime) {
                if (hasFraction) {
                    throw new IllegalArgumentException("Text cannot be parsed to a Duration: " + s);
                }
                secondsAcc += value * SECONDS_PER_HOUR;
            } else if ((unit == 'M' || unit == 'm') && inTime) {
                if (hasFraction) {
                    throw new IllegalArgumentException("Text cannot be parsed to a Duration: " + s);
                }
                secondsAcc += value * SECONDS_PER_MINUTE;
            } else if ((unit == 'S' || unit == 's') && inTime) {
                secondsAcc += value;
                nanoAcc += fracNanos;
            } else {
                throw new IllegalArgumentException("Text cannot be parsed to a Duration: " + s);
            }
            parsedAny = true;
        }
        if (!parsedAny) {
            throw new IllegalArgumentException("Text cannot be parsed to a Duration: " + s);
        }
        Duration d = ofSeconds(secondsAcc, nanoAcc);
        return negate ? d.negated() : d;
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

    public Duration plusNanos(long nanosToAdd) {
        return plus(0, nanosToAdd);
    }

    public Duration minusNanos(long nanosToSubtract) {
        return nanosToSubtract == Long.MIN_VALUE
                ? plusNanos(Long.MAX_VALUE).plusNanos(1)
                : plusNanos(-nanosToSubtract);
    }

    public long toNanos() {
        return seconds * NANOS_PER_SECOND + nanos;
    }

    public long toSeconds() {
        return seconds;
    }

    public boolean isZero() {
        return (seconds | nanos) == 0;
    }

    public boolean isNegative() {
        return seconds < 0;
    }

    public Duration negated() {
        return ofSeconds(-seconds, -(long) nanos);
    }

    public Duration abs() {
        return isNegative() ? negated() : this;
    }

    private BigInteger toTotalNanos() {
        return BigInteger.valueOf(seconds).multiply(BI_NANOS_PER_SECOND).add(BigInteger.valueOf(nanos));
    }

    private static Duration ofBigNanos(BigInteger totalNanos) {
        BigInteger secs = totalNanos.divide(BI_NANOS_PER_SECOND);
        BigInteger nos = totalNanos.remainder(BI_NANOS_PER_SECOND);
        if (secs.bitLength() > 63) {
            throw new ArithmeticException("Exceeds capacity of Duration: " + totalNanos);
        }
        return ofSeconds(secs.longValue(), nos.longValue());
    }

    public Duration multipliedBy(long multiplicand) {
        if (multiplicand == 0) {
            return ZERO;
        }
        if (multiplicand == 1) {
            return this;
        }
        return ofBigNanos(toTotalNanos().multiply(BigInteger.valueOf(multiplicand)));
    }

    public Duration dividedBy(long divisor) {
        if (divisor == 0) {
            throw new ArithmeticException("Cannot divide by zero");
        }
        if (divisor == 1) {
            return this;
        }
        return ofBigNanos(toTotalNanos().divide(BigInteger.valueOf(divisor)));
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
