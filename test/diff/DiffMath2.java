/**
 * Differential coverage for the pure floating-point additions to java.lang.Math:
 * toRadians/toDegrees, copySign(double/float), signum(double/float), ulp(double),
 * nextUp(double), nextDown(double). Results are encoded as ints via scaled value or
 * IEEE-754 bit patterns so bit-exact behavior is checked against the JDK oracle.
 */
public class DiffMath2 {

    public static int toRadians180() {
        return (int) (Math.toRadians(180.0) * 1000000.0); // ~PI -> 3141592
    }

    public static int toDegreesPI() {
        return (int) (Math.toDegrees(Math.PI) * 1000000.0); // ~180 -> 180000000
    }

    public static int copySign3neg1() {
        return (int) (Math.copySign(3.0, -1.0) * 1000000.0); // -3.0 -> -3000000
    }

    public static int copySignFloat() {
        return (int) (Math.copySign(3.0f, -1.0f) * 1000000.0f); // -3.0 -> -3000000
    }

    public static int signumNeg5() {
        return (int) Math.signum(-5.0); // -1
    }

    public static int signumZero() {
        return (int) Math.signum(0.0); // 0
    }

    public static int signumFloatPos() {
        return (int) Math.signum(2.5f); // 1
    }

    public static int ulp1bits() {
        long b = Double.doubleToLongBits(Math.ulp(1.0)); // 2^-52
        return (int) (b >>> 32); // 0x3CB00000 = 1018691584
    }

    public static int nextUp1() {
        long b = Double.doubleToLongBits(Math.nextUp(1.0));
        return (int) (b & 0xffffffL); // 1
    }

    public static int nextDown1() {
        long b = Double.doubleToLongBits(Math.nextDown(1.0));
        return (int) (b & 0xffffffL); // 0xffffff = 16777215
    }
}
