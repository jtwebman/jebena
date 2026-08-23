import java.util.concurrent.TimeUnit;

public class DiffTimeUnit {

    public static int secondsToMillis() {
        return (int) TimeUnit.SECONDS.toMillis(5);
    }

    public static int minutesToSeconds() {
        return (int) TimeUnit.MINUTES.toSeconds(3);
    }

    public static int hoursToMinutes() {
        return (int) TimeUnit.HOURS.toMinutes(2);
    }

    public static int daysToHours() {
        return (int) TimeUnit.DAYS.toHours(1);
    }

    public static int millisToSeconds() {
        return (int) TimeUnit.MILLISECONDS.toSeconds(1500);
    }

    public static int convertMillisToSeconds() {
        return (int) TimeUnit.SECONDS.convert(5000, TimeUnit.MILLISECONDS);
    }

    public static int saturatingDaysToNanos() {
        return (int) TimeUnit.DAYS.toNanos(1000000000L);
    }

    public static int minutesOrdinal() {
        return TimeUnit.MINUTES.ordinal();
    }

    public static int negativeSaturation() {
        return (int) TimeUnit.DAYS.toNanos(-1000000000L);
    }

    public static int valueOfRoundTrip() {
        return TimeUnit.valueOf("HOURS").ordinal() + TimeUnit.values().length;
    }
}
