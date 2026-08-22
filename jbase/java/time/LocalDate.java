package java.time;

/**
 * Clean-room immutable date in the proleptic Gregorian calendar.
 * Fields: year, month (1..12), day (1..lengthOfMonth).
 */
public final class LocalDate implements Comparable<LocalDate> {

    // Days from year 0000-01-01 to 1970-01-01 (proleptic Gregorian).
    private static final long DAYS_0000_TO_1970 = (146097L * 5L) - (30L * 365L + 7L);

    private final int year;
    private final int month;
    private final int day;

    private LocalDate(int year, int month, int day) {
        this.year = year;
        this.month = month;
        this.day = day;
    }

    // ---- long floor helpers (Math only provides int versions) ----
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

    public static LocalDate of(int year, int month, int day) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Invalid value for month: " + month);
        }
        int dom = lengthOfMonth(year, month);
        if (day < 1 || day > dom) {
            throw new IllegalArgumentException("Invalid value for day of month: " + day);
        }
        return new LocalDate(year, month, day);
    }

    public static LocalDate ofEpochDay(long epochDay) {
        long zeroDay = epochDay + DAYS_0000_TO_1970;
        // adjust so the leap day sits at the end of a four-year cycle (0000-03-01)
        zeroDay -= 60;
        long adjust = 0;
        if (zeroDay < 0) {
            long adjustCycles = (zeroDay + 1) / 146097 - 1;
            adjust = adjustCycles * 400;
            zeroDay += -adjustCycles * 146097;
        }
        long yearEst = (400 * zeroDay + 591) / 146097;
        long doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400);
        if (doyEst < 0) {
            yearEst--;
            doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400);
        }
        yearEst += adjust;
        int marchDoy0 = (int) doyEst;
        int marchMonth0 = (marchDoy0 * 5 + 2) / 153;
        int month = (marchMonth0 + 2) % 12 + 1;
        int dom = marchDoy0 - (marchMonth0 * 306 + 5) / 10 + 1;
        yearEst += marchMonth0 / 10;
        return new LocalDate((int) yearEst, month, dom);
    }

    public int getYear() {
        return year;
    }

    public int getMonthValue() {
        return month;
    }

    public int getDayOfMonth() {
        return day;
    }

    public boolean isLeapYear() {
        return isLeapYear(year);
    }

    private static boolean isLeapYear(int year) {
        return ((year & 3) == 0) && ((year % 100 != 0) || (year % 400 == 0));
    }

    public int lengthOfMonth() {
        return lengthOfMonth(year, month);
    }

    public int getDayOfYear() {
        int doy = day;
        for (int m = 1; m < month; m++) {
            doy += lengthOfMonth(year, m);
        }
        return doy;
    }

    public int lengthOfYear() {
        return isLeapYear() ? 366 : 365;
    }

    private static int lengthOfMonth(int year, int month) {
        switch (month) {
            case 2:
                return isLeapYear(year) ? 29 : 28;
            case 4:
            case 6:
            case 9:
            case 11:
                return 30;
            default:
                return 31;
        }
    }

    public long toEpochDay() {
        long y = year;
        long m = month;
        long total = 0;
        total += 365 * y;
        if (y >= 0) {
            total += (y + 3) / 4 - (y + 99) / 100 + (y + 399) / 400;
        } else {
            total -= y / -4 - y / -100 + y / -400;
        }
        total += ((367 * m - 362) / 12);
        total += (day - 1);
        if (m > 2) {
            total--;
            if (!isLeapYear(year)) {
                total--;
            }
        }
        return total - DAYS_0000_TO_1970;
    }

    /** Day of week: 1=Monday .. 7=Sunday. */
    public int getDayOfWeek() {
        int dow0 = (int) floorMod(toEpochDay() + 3, 7);
        return dow0 + 1;
    }

    public LocalDate plusDays(long daysToAdd) {
        if (daysToAdd == 0) {
            return this;
        }
        return ofEpochDay(toEpochDay() + daysToAdd);
    }

    public LocalDate minusDays(long daysToSubtract) {
        return plusDays(-daysToSubtract);
    }

    public LocalDate plusMonths(long monthsToAdd) {
        if (monthsToAdd == 0) {
            return this;
        }
        long monthCount = year * 12L + (month - 1);
        long calcMonths = monthCount + monthsToAdd;
        int newYear = (int) floorDiv(calcMonths, 12);
        int newMonth = (int) floorMod(calcMonths, 12) + 1;
        return resolvePreviousValid(newYear, newMonth, day);
    }

    public LocalDate minusMonths(long monthsToSubtract) {
        return plusMonths(-monthsToSubtract);
    }

    public LocalDate plusYears(long yearsToAdd) {
        if (yearsToAdd == 0) {
            return this;
        }
        int newYear = (int) (year + yearsToAdd);
        return resolvePreviousValid(newYear, month, day);
    }

    public LocalDate minusYears(long yearsToSubtract) {
        return plusYears(-yearsToSubtract);
    }

    private long getProlepticMonth() {
        return year * 12L + (month - 1);
    }

    /** Combine this date with a time to create a LocalDateTime. */
    public LocalDateTime atTime(LocalTime time) {
        return LocalDateTime.of(this, time);
    }

    /** Combine this date with an hour and minute to create a LocalDateTime. */
    public LocalDateTime atTime(int hour, int minute) {
        return LocalDateTime.of(this, LocalTime.of(hour, minute));
    }

    /** Period from this date (inclusive) to the end date (exclusive). */
    public Period until(LocalDate end) {
        long totalMonths = end.getProlepticMonth() - this.getProlepticMonth();
        int days = end.day - this.day;
        if (totalMonths > 0 && days < 0) {
            totalMonths--;
            LocalDate calcDate = this.plusMonths(totalMonths);
            days = (int) (end.toEpochDay() - calcDate.toEpochDay());
        } else if (totalMonths < 0 && days > 0) {
            totalMonths++;
            days -= end.lengthOfMonth();
        }
        long years = totalMonths / 12;
        int months = (int) (totalMonths % 12);
        return Period.of((int) years, months, days);
    }

    private static LocalDate resolvePreviousValid(int year, int month, int day) {
        int dom = lengthOfMonth(year, month);
        if (day > dom) {
            day = dom;
        }
        return new LocalDate(year, month, day);
    }

    public int compareTo(LocalDate other) {
        int cmp = (year - other.year);
        if (cmp == 0) {
            cmp = (month - other.month);
            if (cmp == 0) {
                cmp = (day - other.day);
            }
        }
        return cmp;
    }

    public boolean isBefore(LocalDate other) {
        return compareTo(other) < 0;
    }

    public boolean isAfter(LocalDate other) {
        return compareTo(other) > 0;
    }

    public boolean isEqual(LocalDate other) {
        return compareTo(other) == 0;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof LocalDate) {
            LocalDate o = (LocalDate) obj;
            return year == o.year && month == o.month && day == o.day;
        }
        return false;
    }

    public int hashCode() {
        int yearValue = year;
        int monthValue = month;
        int dayValue = day;
        return (yearValue & 0xFFFFF800) ^ ((yearValue << 11) + (monthValue << 6) + dayValue);
    }

    public String toString() {
        int absYear = year < 0 ? -year : year;
        StringBuilder buf = new StringBuilder(10);
        if (year < 0) {
            buf.append('-');
        }
        if (absYear < 1000) {
            // zero-pad to at least 4 digits
            if (absYear < 10) {
                buf.append("000");
            } else if (absYear < 100) {
                buf.append("00");
            } else {
                buf.append("0");
            }
            buf.append(absYear);
        } else {
            buf.append(absYear);
        }
        buf.append(month < 10 ? "-0" : "-").append(month);
        buf.append(day < 10 ? "-0" : "-").append(day);
        return buf.toString();
    }
}
