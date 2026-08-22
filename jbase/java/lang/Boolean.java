package java.lang;

public final class Boolean implements Comparable<Boolean> {
    public static final Class TYPE = Class.getPrimitiveClass("boolean");

    private final boolean value;

    public static final Boolean TRUE = new Boolean(true);
    public static final Boolean FALSE = new Boolean(false);

    public Boolean(boolean value) {
        this.value = value;
    }

    public static Boolean valueOf(boolean b) {
        return b ? TRUE : FALSE;
    }

    public boolean booleanValue() {
        return value;
    }

    public int hashCode() {
        return value ? 1231 : 1237;
    }

    public boolean equals(Object o) {
        if (o instanceof Boolean) {
            return value == ((Boolean) o).value;
        }
        return false;
    }

    public int compareTo(Boolean other) {
        return (value == other.value) ? 0 : (value ? 1 : -1);
    }

    public String toString() {
        return value ? "true" : "false";
    }

    public static String toString(boolean b) {
        return b ? "true" : "false";
    }

    public static boolean parseBoolean(String s) {
        return s != null && s.equalsIgnoreCase("true");
    }

    public static Boolean valueOf(String s) {
        return parseBoolean(s) ? TRUE : FALSE;
    }
}
