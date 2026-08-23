public class DiffUuid {
    public static int versionV1() {
        java.util.UUID u = java.util.UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        return u.version();
    }

    public static int versionV4() {
        java.util.UUID u = java.util.UUID.fromString("00000000-0000-4000-8000-000000000000");
        return u.version();
    }

    public static int variantBits() {
        java.util.UUID u = java.util.UUID.fromString("00000000-0000-4000-8000-000000000000");
        return u.variant();
    }

    public static int compareSign() {
        java.util.UUID a = java.util.UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        java.util.UUID b = java.util.UUID.fromString("00000000-0000-4000-8000-000000000000");
        int c = a.compareTo(b);
        return c < 0 ? -1 : (c > 0 ? 1 : 0);
    }

    public static int equalsRoundTrip() {
        java.util.UUID a = java.util.UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        java.util.UUID b = java.util.UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        return a.equals(b) ? 1 : 0;
    }

    public static int msbLow() {
        java.util.UUID u = java.util.UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        long msb = u.getMostSignificantBits();
        return (int) msb;
    }

    public static int lsbLow() {
        java.util.UUID u = java.util.UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        long lsb = u.getLeastSignificantBits();
        return (int) lsb;
    }

    public static int toStringRoundTrip() {
        String s = "123e4567-e89b-12d3-a456-426614174000";
        java.util.UUID u = java.util.UUID.fromString(s);
        return s.equals(u.toString()) ? 1 : 0;
    }

    public static int hashMix() {
        java.util.UUID u = java.util.UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        return u.hashCode();
    }
}
