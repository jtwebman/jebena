import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

public class DiffClock {

    public static int fixedMillisLowBits() {
        Clock c = Clock.fixed(Instant.ofEpochMilli(1773082800000L), ZoneOffset.UTC);
        return (int) c.millis();
    }

    public static int fixedInstantEpochSecond() {
        Clock c = Clock.fixed(Instant.ofEpochMilli(1773082800000L), ZoneOffset.UTC);
        return (int) c.instant().getEpochSecond();
    }

    public static int fixedZoneTotalSeconds() {
        Clock c = Clock.fixed(Instant.ofEpochMilli(1773082800000L), ZoneOffset.UTC);
        return ((ZoneOffset) c.getZone()).getTotalSeconds();
    }

    public static int smallFixedMillis() {
        Clock c = Clock.fixed(Instant.ofEpochSecond(100), ZoneOffset.UTC);
        return (int) c.millis();
    }

    public static int fixedInstantSame() {
        Clock c = Clock.fixed(Instant.ofEpochMilli(1773082800000L), ZoneOffset.UTC);
        return c.instant() == c.instant() ? 1 : 0;
    }

    public static int nonUtcZone() {
        Clock c = Clock.fixed(Instant.ofEpochSecond(100), ZoneOffset.ofHours(2));
        return ((ZoneOffset) c.getZone()).getTotalSeconds();
    }

    public static int millisMatchesInstant() {
        Clock c = Clock.fixed(Instant.ofEpochMilli(1773082800000L), ZoneOffset.UTC);
        return c.millis() == c.instant().toEpochMilli() ? 1 : 0;
    }

    public static int negativeEpoch() {
        Clock c = Clock.fixed(Instant.ofEpochSecond(-5), ZoneOffset.UTC);
        return (int) c.millis();
    }
}
