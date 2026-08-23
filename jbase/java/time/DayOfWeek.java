package java.time;

/**
 * Clean-room day-of-week type, MONDAY (1) through SUNDAY (7).
 *
 * <p>Hand-rolled as singleton constants rather than the {@code enum} keyword
 * because the jbase {@link java.lang.Enum} base is non-generic and javac's enum
 * lowering emits a parameterised {@code Enum<E>} supertype it cannot model. The
 * enum-like surface (name/ordinal/values/valueOf plus the domain methods) is
 * reproduced here.
 */
public final class DayOfWeek {

    public static final DayOfWeek MONDAY = new DayOfWeek("MONDAY", 0);
    public static final DayOfWeek TUESDAY = new DayOfWeek("TUESDAY", 1);
    public static final DayOfWeek WEDNESDAY = new DayOfWeek("WEDNESDAY", 2);
    public static final DayOfWeek THURSDAY = new DayOfWeek("THURSDAY", 3);
    public static final DayOfWeek FRIDAY = new DayOfWeek("FRIDAY", 4);
    public static final DayOfWeek SATURDAY = new DayOfWeek("SATURDAY", 5);
    public static final DayOfWeek SUNDAY = new DayOfWeek("SUNDAY", 6);

    private static final DayOfWeek[] VALUES = {
        MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
    };

    private final String name;
    private final int ordinal;

    private DayOfWeek(String name, int ordinal) {
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

    public static DayOfWeek[] values() {
        return VALUES.clone();
    }

    public static DayOfWeek valueOf(String name) {
        for (int i = 0; i < VALUES.length; i++) {
            if (VALUES[i].name.equals(name)) {
                return VALUES[i];
            }
        }
        throw new IllegalArgumentException("No enum constant DayOfWeek." + name);
    }

    public int getValue() {
        return ordinal + 1;
    }

    public static DayOfWeek of(int dayOfWeek) {
        if (dayOfWeek < 1 || dayOfWeek > 7) {
            throw new IllegalArgumentException("Invalid value for DayOfWeek: " + dayOfWeek);
        }
        return VALUES[dayOfWeek - 1];
    }

    public DayOfWeek plus(long days) {
        int amount = (int) (days % 7);
        int index = (ordinal + (amount + 7)) % 7;
        return VALUES[index];
    }

    public DayOfWeek minus(long days) {
        return plus(-(days % 7));
    }
}
