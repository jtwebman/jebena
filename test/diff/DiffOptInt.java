public class DiffOptInt {

    public static int optIntGet() {
        java.util.OptionalInt o = java.util.OptionalInt.of(42);
        return o.getAsInt();
    }

    public static int optIntOrElse() {
        java.util.OptionalInt e = java.util.OptionalInt.empty();
        java.util.OptionalInt p = java.util.OptionalInt.of(99);
        return e.orElse(7) * 31 + p.orElse(7);
    }

    public static int optIntPresent() {
        java.util.OptionalInt p = java.util.OptionalInt.of(0);
        java.util.OptionalInt e = java.util.OptionalInt.empty();
        int acc = 0;
        acc = acc * 31 + (p.isPresent() ? 1 : 0);
        acc = acc * 31 + (e.isPresent() ? 1 : 0);
        acc = acc * 31 + (p.isEmpty() ? 1 : 0);
        acc = acc * 31 + (e.isEmpty() ? 1 : 0);
        return acc;
    }

    public static int optLongGet() {
        java.util.OptionalLong o = java.util.OptionalLong.of(5000000000L);
        long v = o.getAsLong();
        return (int) (v & 0xFFFFFFFFL);
    }

    public static int optLongOrElse() {
        java.util.OptionalLong e = java.util.OptionalLong.empty();
        long v = e.orElse(-123L);
        return (int) v;
    }

    public static int optDoubleOrElse() {
        java.util.OptionalDouble p = java.util.OptionalDouble.of(3.5);
        java.util.OptionalDouble e = java.util.OptionalDouble.empty();
        int a = (int) (p.orElse(0.0) * 2.0);
        int b = (int) (e.orElse(1.25) * 4.0);
        return a * 31 + b;
    }

    public static int optDoubleGet() {
        java.util.OptionalDouble o = java.util.OptionalDouble.of(2.75);
        double v = o.getAsDouble();
        return (int) (v * 100.0);
    }

    public static int optEmptyFlags() {
        int acc = 0;
        acc = acc * 31 + (java.util.OptionalInt.empty().isEmpty() ? 1 : 0);
        acc = acc * 31 + (java.util.OptionalLong.empty().isEmpty() ? 1 : 0);
        acc = acc * 31 + (java.util.OptionalDouble.empty().isEmpty() ? 1 : 0);
        acc = acc * 31 + (java.util.OptionalInt.of(1).isPresent() ? 1 : 0);
        acc = acc * 31 + (java.util.OptionalLong.of(2L).isPresent() ? 1 : 0);
        acc = acc * 31 + (java.util.OptionalDouble.of(3.0).isPresent() ? 1 : 0);
        return acc;
    }
}
