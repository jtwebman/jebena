package java.time;

/**
 * Clean-room instant on the time-line, measured as seconds since the epoch
 * 1970-01-01T00:00:00Z. Nanoseconds fixed at 0.
 */
public final class Instant implements Comparable<Instant> {

    public static final Instant EPOCH = new Instant(0);

    private final long epochSecond;

    private Instant(long epochSecond) {
        this.epochSecond = epochSecond;
    }

    private static long floorDiv(long x, long y) {
        long r = x / y;
        if ((x ^ y) < 0 && r * y != x) {
            r--;
        }
        return r;
    }

    public static Instant ofEpochSecond(long epochSecond) {
        if (epochSecond == 0) {
            return EPOCH;
        }
        return new Instant(epochSecond);
    }

    public static Instant ofEpochMilli(long epochMilli) {
        return ofEpochSecond(floorDiv(epochMilli, 1000));
    }

    public long getEpochSecond() {
        return epochSecond;
    }

    public long toEpochMilli() {
        return epochSecond * 1000;
    }

    public Instant plusSeconds(long secondsToAdd) {
        if (secondsToAdd == 0) {
            return this;
        }
        return ofEpochSecond(epochSecond + secondsToAdd);
    }

    public Instant minusSeconds(long secondsToSubtract) {
        return plusSeconds(-secondsToSubtract);
    }

    public int compareTo(Instant other) {
        if (epochSecond < other.epochSecond) {
            return -1;
        }
        if (epochSecond > other.epochSecond) {
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
            return epochSecond == ((Instant) obj).epochSecond;
        }
        return false;
    }

    public int hashCode() {
        return (int) (epochSecond ^ (epochSecond >>> 32));
    }

    public String toString() {
        return "Instant{epochSecond=" + epochSecond + "}";
    }
}
