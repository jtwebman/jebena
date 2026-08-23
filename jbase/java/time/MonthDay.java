package java.time;

/**
 * Clean-room month-day type: a combination of month-of-year and day-of-month
 * without a year, such as {@code --12-03} for the third of December.
 *
 * <p>A {@code MonthDay} restricts the day-of-month to the largest that the month
 * can ever hold, so February permits 29 (valid only in leap years, checked by
 * {@link #isValidYear(int)}).
 */
public final class MonthDay implements Comparable<MonthDay> {

    private final int month;
    private final int day;

    private MonthDay(int month, int day) {
        this.month = month;
        this.day = day;
    }

    public static MonthDay of(int month, int dayOfMonth) {
        Month m = Month.of(month);
        return of(m, dayOfMonth);
    }

    public static MonthDay of(Month month, int dayOfMonth) {
        if (month == null) {
            throw new NullPointerException("month");
        }
        int mv = month.getValue();
        if (dayOfMonth < 1 || dayOfMonth > 31) {
            throw new IllegalArgumentException("Invalid value for DayOfMonth: " + dayOfMonth);
        }
        if (dayOfMonth > month.maxLength()) {
            throw new IllegalArgumentException(
                "Illegal value for DayOfMonth field, value " + dayOfMonth
                + " is not valid for month " + month.name());
        }
        return new MonthDay(mv, dayOfMonth);
    }

    public int getMonthValue() {
        return month;
    }

    public Month getMonth() {
        return Month.of(month);
    }

    public int getDayOfMonth() {
        return day;
    }

    public boolean isValidYear(int year) {
        if (day == 29 && month == 2 && !isLeapYear(year)) {
            return false;
        }
        return true;
    }

    private static boolean isLeapYear(int year) {
        return ((year & 3) == 0) && ((year % 100 != 0) || (year % 400 == 0));
    }

    public LocalDate atYear(int year) {
        int d = day;
        if (day == 29 && month == 2 && !isLeapYear(year)) {
            d = 28;
        }
        return LocalDate.of(year, month, d);
    }

    public int compareTo(MonthDay o) {
        int cmp = (month - o.month);
        if (cmp == 0) {
            cmp = (day - o.day);
        }
        return cmp;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof MonthDay)) {
            return false;
        }
        MonthDay o = (MonthDay) obj;
        return month == o.month && day == o.day;
    }

    public int hashCode() {
        return (month << 6) + day;
    }

    public String toString() {
        StringBuilder buf = new StringBuilder(7);
        buf.append("--");
        if (month < 10) {
            buf.append('0');
        }
        buf.append(month);
        buf.append('-');
        if (day < 10) {
            buf.append('0');
        }
        buf.append(day);
        return buf.toString();
    }
}
