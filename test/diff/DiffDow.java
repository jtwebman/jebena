import java.time.DayOfWeek;
import java.time.Month;
import java.time.Period;

public class DiffDow {

    public static int dowOfValue() {
        return DayOfWeek.of(3).getValue();
    }

    public static int mondayPlus4() {
        return DayOfWeek.MONDAY.plus(4).getValue();
    }

    public static int sundayPlus1IsMonday() {
        return DayOfWeek.SUNDAY.plus(1).getValue();
    }

    public static int mondayMinus1IsSunday() {
        return DayOfWeek.MONDAY.minus(1).getValue();
    }

    public static int febLeap() {
        return Month.of(2).length(true);
    }

    public static int febNonLeap() {
        return Month.of(2).length(false);
    }

    public static int decemberPlus1IsJanuary() {
        return Month.DECEMBER.plus(1).getValue();
    }

    public static int mayFirstMonthOfQuarter() {
        return Month.of(5).firstMonthOfQuarter().getValue();
    }

    public static int fridayValue() {
        return DayOfWeek.valueOf("FRIDAY").getValue();
    }

    public static int periodNormalized() {
        Period p = Period.of(1, 14, 5).normalized();
        return p.getYears() * 100 + p.getMonths();
    }
}
