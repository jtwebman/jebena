import java.time.Duration;

public class DiffDuration {

    public static int parseMinutes() {
        return (int) Duration.parse("PT1H30M").toMinutes();
    }

    public static int parseHours() {
        return (int) Duration.parse("P1DT2H").toHours();
    }

    public static int parseNegHours() {
        return (int) Duration.parse("PT-6H").toHours();
    }

    public static int ofNanosSeconds() {
        return (int) Duration.ofNanos(1500000000L).getSeconds();
    }

    public static int ofNanosNano() {
        return Duration.ofNanos(1500000000L).getNano();
    }

    public static int multiplied() {
        return (int) Duration.ofSeconds(10).multipliedBy(3).toSeconds();
    }

    public static int divided() {
        return (int) Duration.ofMinutes(2).dividedBy(4).toSeconds();
    }

    public static int isNeg() {
        return Duration.ofSeconds(5).negated().isNegative() ? 1 : 0;
    }

    public static int absPos() {
        return (int) Duration.ofSeconds(-5).abs().getSeconds();
    }

    public static int toNanosCase() {
        return (int) Duration.parse("PT0.25S").toNanos();
    }

    public static int toStringChecksum() {
        String s = Duration.parse("PT1H30M45.5S").toString();
        int sum = 0;
        for (int i = 0; i < s.length(); i++) {
            sum = sum * 31 + s.charAt(i);
        }
        return sum;
    }
}
