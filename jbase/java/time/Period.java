package java.time;

/**
 * Clean-room date-based amount of time: years, months and days.
 */
public final class Period {

    public static final Period ZERO = new Period(0, 0, 0);

    private final int years;
    private final int months;
    private final int days;

    private Period(int years, int months, int days) {
        this.years = years;
        this.months = months;
        this.days = days;
    }

    private static Period create(int years, int months, int days) {
        if ((years | months | days) == 0) {
            return ZERO;
        }
        return new Period(years, months, days);
    }

    public static Period of(int years, int months, int days) {
        return create(years, months, days);
    }

    public static Period ofYears(int years) {
        return create(years, 0, 0);
    }

    public static Period ofMonths(int months) {
        return create(0, months, 0);
    }

    /** Period between two dates (start inclusive, end exclusive). */
    public static Period between(LocalDate startInclusive, LocalDate endExclusive) {
        return startInclusive.until(endExclusive);
    }

    public static Period ofDays(int days) {
        return create(0, 0, days);
    }

    public int getYears() {
        return years;
    }

    public int getMonths() {
        return months;
    }

    public int getDays() {
        return days;
    }

    public Period plus(Period other) {
        return create(years + other.years, months + other.months, days + other.days);
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Period) {
            Period o = (Period) obj;
            return years == o.years && months == o.months && days == o.days;
        }
        return false;
    }

    public int hashCode() {
        return years + (months << 8) + (days << 16);
    }

    public String toString() {
        if (years == 0 && months == 0 && days == 0) {
            return "P0D";
        }
        StringBuilder buf = new StringBuilder();
        buf.append('P');
        if (years != 0) {
            buf.append(years).append('Y');
        }
        if (months != 0) {
            buf.append(months).append('M');
        }
        if (days != 0) {
            buf.append(days).append('D');
        }
        return buf.toString();
    }
}
