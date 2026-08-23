import java.time.Year;
import java.time.YearMonth;

public class DiffYear {

    private static int checksum(String s) {
        int h = 0;
        for (int i = 0; i < s.length(); i++) {
            h = h * 31 + s.charAt(i);
        }
        return h;
    }

    public static int yearLeap2024() {
        return Year.of(2024).isLeap() ? 1 : 0;
    }

    public static int yearLeap2026() {
        return Year.of(2026).isLeap() ? 1 : 0;
    }

    public static int yearLen2026() {
        return Year.of(2026).length();
    }

    public static int yearLen2024() {
        return Year.of(2024).length();
    }

    public static int yearPlus() {
        return Year.of(2020).plusYears(5).getValue();
    }

    public static int yearMinus() {
        return Year.of(2020).minusYears(30).getValue();
    }

    public static int yearAtDay() {
        return (int) Year.of(2024).atDay(60).toEpochDay();
    }

    public static int yearToString() {
        return checksum(Year.of(2026).toString());
    }

    public static int ymFebNormal() {
        return YearMonth.of(2026, 2).lengthOfMonth();
    }

    public static int ymFebLeap() {
        return YearMonth.of(2024, 2).lengthOfMonth();
    }

    public static int ymLenYear() {
        return YearMonth.of(2024, 2).lengthOfYear();
    }

    public static int ymPlusRoll() {
        return checksum(YearMonth.of(2026, 1).plusMonths(13).toString());
    }

    public static int ymMonthValue() {
        return YearMonth.of(2026, 3).getMonthValue();
    }

    public static int ymMinusRoll() {
        return checksum(YearMonth.of(2026, 1).minusMonths(2).toString());
    }

    public static int ymEndOfMonth() {
        return YearMonth.of(2024, 2).atEndOfMonth().getDayOfMonth();
    }

    public static int ymToString() {
        return checksum(YearMonth.of(7, 3).toString());
    }
}
