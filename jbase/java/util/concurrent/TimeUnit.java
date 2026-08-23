package java.util.concurrent;

/**
 * Clean-room {@code TimeUnit}: a granularity for time durations and a set of
 * conversion helpers between them.
 *
 * <p>Hand-rolled as singleton constants rather than the {@code enum} keyword
 * because the jbase {@link java.lang.Enum} base is non-generic and javac's enum
 * lowering emits a parameterised {@code Enum<E>} supertype it cannot model,
 * while javac also forbids extending {@code java.lang.Enum} directly. The
 * enum-like surface (name/ordinal/values/valueOf) plus the documented
 * conversion methods are reproduced here.
 *
 * <p>Each unit carries its size expressed in nanoseconds. Conversions to a
 * coarser unit divide (losing precision); conversions to a finer unit multiply
 * and saturate to {@link Long#MAX_VALUE} or {@link Long#MIN_VALUE} on overflow,
 * matching the JDK's contract.
 */
public final class TimeUnit {

    // Scale of each unit, expressed in nanoseconds.
    private static final long NANO_SCALE = 1L;
    private static final long MICRO_SCALE = 1000L * NANO_SCALE;
    private static final long MILLI_SCALE = 1000L * MICRO_SCALE;
    private static final long SECOND_SCALE = 1000L * MILLI_SCALE;
    private static final long MINUTE_SCALE = 60L * SECOND_SCALE;
    private static final long HOUR_SCALE = 60L * MINUTE_SCALE;
    private static final long DAY_SCALE = 24L * HOUR_SCALE;

    public static final TimeUnit NANOSECONDS =
        new TimeUnit("NANOSECONDS", 0, NANO_SCALE);
    public static final TimeUnit MICROSECONDS =
        new TimeUnit("MICROSECONDS", 1, MICRO_SCALE);
    public static final TimeUnit MILLISECONDS =
        new TimeUnit("MILLISECONDS", 2, MILLI_SCALE);
    public static final TimeUnit SECONDS =
        new TimeUnit("SECONDS", 3, SECOND_SCALE);
    public static final TimeUnit MINUTES =
        new TimeUnit("MINUTES", 4, MINUTE_SCALE);
    public static final TimeUnit HOURS =
        new TimeUnit("HOURS", 5, HOUR_SCALE);
    public static final TimeUnit DAYS =
        new TimeUnit("DAYS", 6, DAY_SCALE);

    private static final TimeUnit[] VALUES = {
        NANOSECONDS, MICROSECONDS, MILLISECONDS, SECONDS, MINUTES, HOURS, DAYS
    };

    private final String name;
    private final int ordinal;
    private final long scale;

    private TimeUnit(String name, int ordinal, long scale) {
        this.name = name;
        this.ordinal = ordinal;
        this.scale = scale;
    }

    public String name() {
        return name;
    }

    public int ordinal() {
        return ordinal;
    }

    public String toString() {
        return name;
    }

    public static TimeUnit[] values() {
        return VALUES.clone();
    }

    public static TimeUnit valueOf(String name) {
        for (int i = 0; i < VALUES.length; i++) {
            if (VALUES[i].name.equals(name)) {
                return VALUES[i];
            }
        }
        throw new IllegalArgumentException("No enum constant TimeUnit." + name);
    }

    /**
     * Converts {@code d} (measured in a unit of nanosecond-size {@code src})
     * into a unit of nanosecond-size {@code dst}. Coarsening divides; refining
     * multiplies with saturation on overflow.
     */
    private static long cvt(long d, long dst, long src) {
        long r;
        long m;
        if (src == dst) {
            return d;
        } else if (src < dst) {
            return d / (dst / src);
        } else if (d > (m = Long.MAX_VALUE / (r = src / dst))) {
            return Long.MAX_VALUE;
        } else if (d < -m) {
            return Long.MIN_VALUE;
        } else {
            return d * r;
        }
    }

    /**
     * Converts the given duration in the given unit to this unit.
     */
    public long convert(long sourceDuration, TimeUnit sourceUnit) {
        return cvt(sourceDuration, scale, sourceUnit.scale);
    }

    public long toNanos(long duration) {
        return cvt(duration, NANO_SCALE, scale);
    }

    public long toMicros(long duration) {
        return cvt(duration, MICRO_SCALE, scale);
    }

    public long toMillis(long duration) {
        return cvt(duration, MILLI_SCALE, scale);
    }

    public long toSeconds(long duration) {
        return cvt(duration, SECOND_SCALE, scale);
    }

    public long toMinutes(long duration) {
        return cvt(duration, MINUTE_SCALE, scale);
    }

    public long toHours(long duration) {
        return cvt(duration, HOUR_SCALE, scale);
    }

    public long toDays(long duration) {
        return cvt(duration, DAY_SCALE, scale);
    }
}
