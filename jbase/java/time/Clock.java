package java.time;

/**
 * Clean-room clock providing access to the current instant and zone. The
 * {@link #fixed} factory yields a deterministic, immutable clock that always
 * reports the same instant; {@link #systemUTC} and {@link #system} read the
 * host clock via {@code System.currentTimeMillis}.
 *
 * <p>Zones are typed as {@link ZoneId} to match the platform contract, so a
 * fixture compiled against the reference API links against the same method
 * descriptors. Callers pass a concrete {@link ZoneOffset}.
 */
public final class Clock {

    private final Instant fixedInstant;
    private final ZoneId zone;

    private Clock(Instant fixedInstant, ZoneId zone) {
        this.fixedInstant = fixedInstant;
        this.zone = zone;
    }

    /**
     * A clock that always returns the same instant, in the given zone.
     */
    public static Clock fixed(Instant fixedInstant, ZoneId zone) {
        if (fixedInstant == null) {
            throw new NullPointerException("fixedInstant");
        }
        if (zone == null) {
            throw new NullPointerException("zone");
        }
        return new Clock(fixedInstant, zone);
    }

    /**
     * A clock using the host time in the UTC zone. Not deterministic.
     */
    public static Clock systemUTC() {
        return new Clock(null, ZoneOffset.UTC);
    }

    /**
     * A clock using the host time in the given zone. Not deterministic.
     */
    public static Clock system(ZoneId zone) {
        if (zone == null) {
            throw new NullPointerException("zone");
        }
        return new Clock(null, zone);
    }

    /**
     * The zone used to interpret this clock.
     */
    public ZoneId getZone() {
        return zone;
    }

    /**
     * The current instant of this clock.
     */
    public Instant instant() {
        if (fixedInstant != null) {
            return fixedInstant;
        }
        return Instant.ofEpochMilli(System.currentTimeMillis());
    }

    /**
     * The current millisecond instant of this clock.
     */
    public long millis() {
        if (fixedInstant != null) {
            return fixedInstant.toEpochMilli();
        }
        return System.currentTimeMillis();
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Clock)) {
            return false;
        }
        Clock other = (Clock) obj;
        if (!zone.equals(other.zone)) {
            return false;
        }
        if (fixedInstant == null) {
            return other.fixedInstant == null;
        }
        return fixedInstant.equals(other.fixedInstant);
    }

    public int hashCode() {
        int result = zone.hashCode();
        result = 31 * result + (fixedInstant == null ? 0 : fixedInstant.hashCode());
        return result;
    }

    public String toString() {
        if (fixedInstant != null) {
            return "FixedClock[" + fixedInstant + "," + zone + "]";
        }
        return "SystemClock[" + zone + "]";
    }
}
