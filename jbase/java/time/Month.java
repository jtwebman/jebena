package java.time;

/**
 * Clean-room month-of-year type, JANUARY (1) through DECEMBER (12).
 *
 * <p>Hand-rolled as singleton constants rather than the {@code enum} keyword
 * because the jbase {@link java.lang.Enum} base is non-generic and javac's enum
 * lowering emits a parameterised {@code Enum<E>} supertype it cannot model. The
 * enum-like surface (name/ordinal/values/valueOf plus the domain methods) is
 * reproduced here.
 */
public final class Month {

    public static final Month JANUARY = new Month("JANUARY", 0);
    public static final Month FEBRUARY = new Month("FEBRUARY", 1);
    public static final Month MARCH = new Month("MARCH", 2);
    public static final Month APRIL = new Month("APRIL", 3);
    public static final Month MAY = new Month("MAY", 4);
    public static final Month JUNE = new Month("JUNE", 5);
    public static final Month JULY = new Month("JULY", 6);
    public static final Month AUGUST = new Month("AUGUST", 7);
    public static final Month SEPTEMBER = new Month("SEPTEMBER", 8);
    public static final Month OCTOBER = new Month("OCTOBER", 9);
    public static final Month NOVEMBER = new Month("NOVEMBER", 10);
    public static final Month DECEMBER = new Month("DECEMBER", 11);

    private static final Month[] VALUES = {
        JANUARY, FEBRUARY, MARCH, APRIL, MAY, JUNE,
        JULY, AUGUST, SEPTEMBER, OCTOBER, NOVEMBER, DECEMBER
    };

    private final String name;
    private final int ordinal;

    private Month(String name, int ordinal) {
        this.name = name;
        this.ordinal = ordinal;
    }

    public String name() {
        return name;
    }

    public int ordinal() {
        return ordinal;
    }

    public String toString() {
        return name;
    }

    public static Month[] values() {
        return VALUES.clone();
    }

    public static Month valueOf(String name) {
        for (int i = 0; i < VALUES.length; i++) {
            if (VALUES[i].name.equals(name)) {
                return VALUES[i];
            }
        }
        throw new IllegalArgumentException("No enum constant Month." + name);
    }

    public int getValue() {
        return ordinal + 1;
    }

    public static Month of(int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Invalid value for Month: " + month);
        }
        return VALUES[month - 1];
    }

    public int length(boolean leapYear) {
        switch (ordinal) {
            case 1:
                return leapYear ? 29 : 28;
            case 3:
            case 5:
            case 8:
            case 10:
                return 30;
            default:
                return 31;
        }
    }

    public int minLength() {
        return ordinal == 1 ? 28 : length(false);
    }

    public int maxLength() {
        return ordinal == 1 ? 29 : length(false);
    }

    public Month plus(long months) {
        int amount = (int) (months % 12);
        int index = (ordinal + (amount + 12)) % 12;
        return VALUES[index];
    }

    public Month minus(long months) {
        return plus(-(months % 12));
    }

    public Month firstMonthOfQuarter() {
        return VALUES[(ordinal / 3) * 3];
    }
}
