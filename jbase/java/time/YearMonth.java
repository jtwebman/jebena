package java.time;

/**
 * Clean-room immutable year-month in the proleptic Gregorian calendar.
 */
public final class YearMonth implements Comparable {

    private final int year;
    private final int month;

    private YearMonth(int year, int month) {
        this.year = year;
        this.month = month;
    }

    public static YearMonth of(int year, int month) {
        if (year < Year.MIN_VALUE || year > Year.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid value for year: " + year);
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Invalid value for month: " + month);
        }
        return new YearMonth(year, month);
    }

    public int getYear() {
        return year;
    }

    public int getMonthValue() {
        return month;
    }

    public boolean isLeapYear() {
        return Year.isLeap(year);
    }

    public int lengthOfMonth() {
        switch (month) {
            case 2:
                return isLeapYear() ? 29 : 28;
            case 4:
            case 6:
            case 9:
            case 11:
                return 30;
            default:
                return 31;
        }
    }

    public int lengthOfYear() {
        return isLeapYear() ? 366 : 365;
    }

    public boolean isValidDay(int dayOfMonth) {
        return dayOfMonth >= 1 && dayOfMonth <= lengthOfMonth();
    }

    public YearMonth plusMonths(long monthsToAdd) {
        if (monthsToAdd == 0L) {
            return this;
        }
        long monthCount = year * 12L + (month - 1);
        long calcMonths = monthCount + monthsToAdd;
        int newYear = (int) checkYear(floorDiv(calcMonths, 12L));
        int newMonth = (int) floorMod(calcMonths, 12L) + 1;
        return new YearMonth(newYear, newMonth);
    }

    public YearMonth minusMonths(long monthsToSubtract) {
        if (monthsToSubtract == Long.MIN_VALUE) {
            return plusMonths(Long.MAX_VALUE).plusMonths(1L);
        }
        return plusMonths(-monthsToSubtract);
    }

    public YearMonth plusYears(long yearsToAdd) {
        if (yearsToAdd == 0L) {
            return this;
        }
        int newYear = (int) checkYear(year + yearsToAdd);
        return new YearMonth(newYear, month);
    }

    public YearMonth minusYears(long yearsToSubtract) {
        if (yearsToSubtract == Long.MIN_VALUE) {
            return plusYears(Long.MAX_VALUE).plusYears(1L);
        }
        return plusYears(-yearsToSubtract);
    }

    public LocalDate atDay(int dayOfMonth) {
        return LocalDate.of(year, month, dayOfMonth);
    }

    public LocalDate atEndOfMonth() {
        return LocalDate.of(year, month, lengthOfMonth());
    }

    public boolean isAfter(YearMonth other) {
        return compareTo(other) > 0;
    }

    public boolean isBefore(YearMonth other) {
        return compareTo(other) < 0;
    }

    public int compareTo(Object o) {
        YearMonth other = (YearMonth) o;
        int cmp = year - other.year;
        if (cmp == 0) {
            cmp = month - other.month;
        }
        return cmp;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof YearMonth) {
            YearMonth other = (YearMonth) obj;
            return year == other.year && month == other.month;
        }
        return false;
    }

    public int hashCode() {
        return year ^ (month << 27);
    }

    public String toString() {
        int absYear = Math.abs(year);
        StringBuilder buf = new StringBuilder(9);
        if (absYear < 1000) {
            if (year < 0) {
                buf.append(year - 10000).deleteCharAt(1);
            } else {
                buf.append(year + 10000).deleteCharAt(0);
            }
        } else {
            buf.append(year);
        }
        buf.append(month < 10 ? "-0" : "-").append(month);
        return buf.toString();
    }

    private static long checkYear(long y) {
        if (y < Year.MIN_VALUE || y > Year.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid value for year: " + y);
        }
        return y;
    }

    private static long floorDiv(long x, long y) {
        long r = x / y;
        if ((x ^ y) < 0 && r * y != x) {
            r--;
        }
        return r;
    }

    private static long floorMod(long x, long y) {
        return x - floorDiv(x, y) * y;
    }
}
