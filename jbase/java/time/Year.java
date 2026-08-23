package java.time;

/**
 * Clean-room immutable year in the proleptic Gregorian calendar.
 */
public final class Year implements Comparable {

    public static final int MIN_VALUE = -999999999;
    public static final int MAX_VALUE = 999999999;

    private final int year;

    private Year(int year) {
        this.year = year;
    }

    public static Year of(int isoYear) {
        if (isoYear < MIN_VALUE || isoYear > MAX_VALUE) {
            throw new IllegalArgumentException("Invalid value for year: " + isoYear);
        }
        return new Year(isoYear);
    }

    public int getValue() {
        return year;
    }

    public boolean isLeap() {
        return isLeap(year);
    }

    public static boolean isLeap(long year) {
        return ((year & 3L) == 0L) && ((year % 100L != 0L) || (year % 400L == 0L));
    }

    public int length() {
        return isLeap(year) ? 366 : 365;
    }

    public Year plusYears(long yearsToAdd) {
        if (yearsToAdd == 0L) {
            return this;
        }
        return of((int) checkYear(year + yearsToAdd));
    }

    public Year minusYears(long yearsToSubtract) {
        if (yearsToSubtract == Long.MIN_VALUE) {
            return plusYears(Long.MAX_VALUE).plusYears(1L);
        }
        return plusYears(-yearsToSubtract);
    }

    private static long checkYear(long y) {
        if (y < MIN_VALUE || y > MAX_VALUE) {
            throw new IllegalArgumentException("Invalid value for year: " + y);
        }
        return y;
    }

    public YearMonth atMonth(int month) {
        return YearMonth.of(year, month);
    }

    public LocalDate atDay(int dayOfYear) {
        LocalDate jan1 = LocalDate.of(year, 1, 1);
        int max = length();
        if (dayOfYear < 1 || dayOfYear > max) {
            throw new IllegalArgumentException("Invalid value for dayOfYear: " + dayOfYear);
        }
        return jan1.plusDays(dayOfYear - 1);
    }

    public boolean isAfter(Year other) {
        return year > other.year;
    }

    public boolean isBefore(Year other) {
        return year < other.year;
    }

    public int compareTo(Object o) {
        Year other = (Year) o;
        return year - other.year;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Year) {
            return year == ((Year) obj).year;
        }
        return false;
    }

    public int hashCode() {
        return year;
    }

    public String toString() {
        return Integer.toString(year);
    }
}
