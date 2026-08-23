import java.time.Period;

public class DiffPeriod {

    static int sum(String s) {
        int h = 0;
        for (int i = 0; i < s.length(); i++) {
            h = h * 31 + s.charAt(i);
        }
        return h;
    }

    public static int parseYMD() {
        Period p = Period.parse("P1Y2M3D");
        return p.getYears() * 10000 + p.getMonths() * 100 + p.getDays();
    }

    public static int parseNegYear() {
        Period p = Period.parse("P-1Y2M");
        return p.getYears();
    }

    public static int parseNegYearMonth() {
        Period p = Period.parse("P-1Y2M");
        return p.getMonths();
    }

    public static int parseWeeks() {
        Period p = Period.parse("P20D");
        return p.getDays();
    }

    public static int parseWeekUnit() {
        Period p = Period.parse("P2W");
        return p.getDays();
    }

    public static int multipliedByTotal() {
        return (int) Period.of(1, 2, 3).multipliedBy(3).toTotalMonths();
    }

    public static int negatedYears() {
        return Period.of(1, 2, 3).negated().getYears();
    }

    public static int totalMonths() {
        return (int) Period.of(2, 3, 0).toTotalMonths();
    }

    public static int roundTripToString() {
        Period p = Period.parse("P1Y2M3D");
        return sum(p.toString());
    }

    public static int isZeroFlag() {
        return Period.ZERO.isZero() ? 1 : 0;
    }

    public static int isNegativeFlag() {
        return Period.of(-1, 0, 5).isNegative() ? 1 : 0;
    }

    public static int minusPlus() {
        Period p = Period.of(5, 5, 5).plusYears(2).minusMonths(3).plusDays(10);
        return p.getYears() * 10000 + p.getMonths() * 100 + p.getDays();
    }
}
