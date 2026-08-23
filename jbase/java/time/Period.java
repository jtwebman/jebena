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

    public static Period parse(CharSequence text) {
        if (text == null) {
            throw new NullPointerException("text");
        }
        String s = text.toString();
        int len = s.length();
        int pos = 0;
        int sign = 1;
        char c = pos < len ? s.charAt(pos) : 0;
        if (c == '+' || c == '-') {
            if (c == '-') {
                sign = -1;
            }
            pos++;
        }
        if (pos >= len || (s.charAt(pos) != 'P' && s.charAt(pos) != 'p')) {
            throw new IllegalArgumentException("Text cannot be parsed to a Period: " + text);
        }
        pos++;
        long years = 0;
        long months = 0;
        long days = 0;
        boolean any = false;
        // order must be Y, M, W, D
        int stage = 0; // 0=before Y,1=before M,2=before W,3=before D,4=done
        while (pos < len) {
            // parse optional sign + digits
            int numStart = pos;
            int numSign = 1;
            char nc = s.charAt(pos);
            if (nc == '+' || nc == '-') {
                if (nc == '-') {
                    numSign = -1;
                }
                pos++;
            }
            int digStart = pos;
            while (pos < len && Character.isDigit(s.charAt(pos))) {
                pos++;
            }
            if (pos == digStart) {
                throw new IllegalArgumentException("Text cannot be parsed to a Period: " + text + " at " + numStart);
            }
            long value = 0;
            for (int i = digStart; i < pos; i++) {
                value = value * 10 + (s.charAt(i) - '0');
            }
            value *= numSign;
            if (pos >= len) {
                throw new IllegalArgumentException("Text cannot be parsed to a Period: " + text);
            }
            char unit = s.charAt(pos);
            pos++;
            if ((unit == 'Y' || unit == 'y') && stage <= 0) {
                years = value;
                stage = 1;
            } else if ((unit == 'M' || unit == 'm') && stage <= 1) {
                months = value;
                stage = 2;
            } else if ((unit == 'W' || unit == 'w') && stage <= 2) {
                days += value * 7L;
                stage = 3;
            } else if ((unit == 'D' || unit == 'd') && stage <= 3) {
                days += value;
                stage = 4;
            } else {
                throw new IllegalArgumentException("Text cannot be parsed to a Period: " + text);
            }
            any = true;
        }
        if (!any) {
            throw new IllegalArgumentException("Text cannot be parsed to a Period: " + text);
        }
        years *= sign;
        months *= sign;
        days *= sign;
        int iy = (int) years;
        int im = (int) months;
        int id = (int) days;
        if (iy != years || im != months || id != days) {
            throw new IllegalArgumentException("Text cannot be parsed to a Period: overflow " + text);
        }
        return create(iy, im, id);
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

    public long toTotalMonths() {
        return (long) years * 12 + months;
    }

    public boolean isZero() {
        return (years | months | days) == 0;
    }

    public boolean isNegative() {
        return years < 0 || months < 0 || days < 0;
    }

    public Period multipliedBy(int scalar) {
        if (scalar == 1 || (years | months | days) == 0) {
            return this;
        }
        return create(years * scalar, months * scalar, days * scalar);
    }

    public Period negated() {
        return multipliedBy(-1);
    }

    public Period plusYears(long yearsToAdd) {
        if (yearsToAdd == 0) {
            return this;
        }
        return create(addToInt(years, yearsToAdd), months, days);
    }

    public Period plusMonths(long monthsToAdd) {
        if (monthsToAdd == 0) {
            return this;
        }
        return create(years, addToInt(months, monthsToAdd), days);
    }

    public Period plusDays(long daysToAdd) {
        if (daysToAdd == 0) {
            return this;
        }
        return create(years, months, addToInt(days, daysToAdd));
    }

    public Period minusYears(long yearsToSubtract) {
        return plusYears(-yearsToSubtract);
    }

    public Period minusMonths(long monthsToSubtract) {
        return plusMonths(-monthsToSubtract);
    }

    public Period minusDays(long daysToSubtract) {
        return plusDays(-daysToSubtract);
    }

    private static int addToInt(int base, long amount) {
        long sum = base + amount;
        int result = (int) sum;
        if (result != sum) {
            throw new ArithmeticException("integer overflow");
        }
        return result;
    }

    /** Rolls months into years so months is in the range -11..11. */
    public Period normalized() {
        long totalMonths = (long) years * 12 + months;
        int splitYears = (int) (totalMonths / 12);
        int splitMonths = (int) (totalMonths % 12);
        if (splitYears == years && splitMonths == months) {
            return this;
        }
        return create(splitYears, splitMonths, days);
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
