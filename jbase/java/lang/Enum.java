package java.lang;

// Clean-room base class for enum types. javac lowers each `enum` to a final
// class extending this, with static final constants, a synthetic $VALUES array,
// and generated static values()/valueOf(String); the generated valueOf calls
// Enum.valueOf(Class, String), which finds the constant by name via
// Class.getEnumConstants() (the enum's synthetic values()).
public abstract class Enum {
    private final String name;
    private final int ordinal;

    protected Enum(String name, int ordinal) {
        this.name = name;
        this.ordinal = ordinal;
    }

    public final String name() {
        return name;
    }

    public final int ordinal() {
        return ordinal;
    }

    public String toString() {
        return name;
    }

    public final boolean equals(Object other) {
        return this == other;
    }

    public final int hashCode() {
        return super.hashCode();
    }

    public final int compareTo(Enum other) {
        return this.ordinal - other.ordinal;
    }

    public static Enum valueOf(Class enumType, String name) {
        Object[] constants = enumType.getEnumConstants();
        if (constants != null) {
            for (int i = 0; i < constants.length; i++) {
                Enum e = (Enum) constants[i];
                if (e.name().equals(name)) {
                    return e;
                }
            }
        }
        throw new IllegalArgumentException("No enum constant " + name);
    }
}
