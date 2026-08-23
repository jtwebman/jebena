import java.time.MonthDay;
import java.time.Month;
import java.time.LocalDate;

public class DiffMonthDay {

    static int sum(String s) {
        int h = 0;
        for (int i = 0; i < s.length(); i++) {
            h = h * 31 + s.charAt(i);
        }
        return h;
    }

    public static int toStringBasic() {
        return sum(MonthDay.of(3, 9).toString());
    }

    public static int monthValueDay() {
        MonthDay md = MonthDay.of(12, 25);
        return md.getMonthValue() * 100 + md.getDayOfMonth();
    }

    public static int febValidLeap() {
        return MonthDay.of(2, 29).isValidYear(2024) ? 1 : 0;
    }

    public static int febValidNonLeap() {
        return MonthDay.of(2, 29).isValidYear(2026) ? 1 : 0;
    }

    public static int compareSign() {
        int c = MonthDay.of(1, 15).compareTo(MonthDay.of(6, 1));
        return c < 0 ? -1 : (c > 0 ? 1 : 0);
    }

    public static int atYearDayOfYear() {
        return MonthDay.of(7, 4).atYear(2026).getDayOfYear();
    }

    public static int ofMonthEnum() {
        return sum(MonthDay.of(Month.AUGUST, 21).toString());
    }

    public static int atYearFebNonLeap() {
        LocalDate d = MonthDay.of(2, 29).atYear(2026);
        return d.getMonthValue() * 100 + d.getDayOfMonth();
    }

    public static int equalsHash() {
        MonthDay a = MonthDay.of(5, 10);
        MonthDay b = MonthDay.of(5, 10);
        return (a.equals(b) ? 1 : 0) + (a.hashCode() == b.hashCode() ? 10 : 0);
    }

    public static int getMonthName() {
        return sum(MonthDay.of(11, 3).getMonth().name());
    }
}
