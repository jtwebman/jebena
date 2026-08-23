/**
 * Differential coverage for java.lang.Math floating-point additions:
 * rint (round-half-to-even), getExponent, scalb, nextAfter, plus long
 * floorDiv/floorMod. Doubles are encoded as ints so bit-exact behavior is
 * checked: integral results via (int) cast, small fractions via *1_000_000,
 * and nextAfter via the low 32 bits of the raw double bit pattern.
 */
public class DiffMath3 {

    public static int rintHalfEven() {
        // Ties round to the nearest even integer.
        int a = (int) Math.rint(2.5); // 2
        int b = (int) Math.rint(3.5); // 4
        int c = (int) Math.rint(-2.5); // -2
        int d = (int) Math.rint(0.5); // 0
        return a * 1000 + (b + 10) * 100 + (c + 10) * 10 + d;
        // 2000 + 1400 + 80 + 0 = 3480
    }

    public static int rintNonTie() {
        int a = (int) Math.rint(2.4); // 2
        int b = (int) Math.rint(2.6); // 3
        int c = (int) Math.rint(-2.6); // -3
        return a * 100 + (b + 10) * 10 + (c + 10);
        // 200 + 130 + 7 = 337
    }

    public static int getExponent() {
        int a = Math.getExponent(8.0); // 3
        int b = Math.getExponent(1.0); // 0
        int c = Math.getExponent(0.5); // -1
        int d = Math.getExponent(0.0); // -1023 (MIN_EXPONENT - 1)
        int e = Math.getExponent(Double.POSITIVE_INFINITY); // 1024 (MAX_EXPONENT + 1)
        return a * 1000000 + (b + 5) * 100000 + (c + 5) * 10000 + (d + 2000) * 10 + e;
        // 3000000 + 500000 + 40000 + 9770 + 1024 = 3550794
    }

    public static int scalbInt() {
        int a = (int) Math.scalb(1.0, 4); // 16
        int b = (int) Math.scalb(3.0, 3); // 24
        int c = (int) Math.scalb(1024.0, -2); // 256
        return a * 1000 + b * 100 + c;
        // 16000 + 2400 + 256 = 18656
    }

    public static int scalbFrac() {
        double x = Math.scalb(1.0, -4); // 0.0625
        double y = Math.scalb(5.0, -1); // 2.5
        return (int) (x * 1000000) + (int) (y * 1000000);
        // 62500 + 2500000 = 2562500
    }

    public static int nextAfterLowBits() {
        // nextAfter(1.0, 2.0) is the next double above 1.0; its raw bits are
        // 0x3FF0000000000001, so the low 32 bits are 1.
        double n = Math.nextAfter(1.0, 2.0);
        return (int) Double.doubleToRawLongBits(n); // 1
    }

    public static int nextAfterDown() {
        // nextAfter(1.0, 0.0) is the next double below 1.0: 0x3FEFFFFFFFFFFFFF,
        // whose low 32 bits are 0xFFFFFFFF = -1.
        double n = Math.nextAfter(1.0, 0.0);
        return (int) Double.doubleToRawLongBits(n); // -1
    }

    public static int nextAfterZero() {
        // From +0.0 toward -inf gives -Double.MIN_VALUE (bits 0x8000000000000001).
        double n = Math.nextAfter(0.0, -1.0);
        int low = (int) Double.doubleToRawLongBits(n); // 1
        int equal = (Math.nextAfter(3.0, 3.0) == 3.0) ? 1 : 0; // 1
        return low * 10 + equal; // 11
    }

    public static int floorDivMod() {
        long a = Math.floorDiv(-7L, 2L); // -4
        long b = Math.floorMod(-7L, 2L); // 1
        long c = Math.floorDiv(7L, -2L); // -4
        long d = Math.floorMod(7L, -2L); // -1
        return (int) (a * 1000 + (b + 10) * 100 + (c + 10) * 10 + (d + 10));
        // -4000 + 1100 + 60 + 9 = -2831
    }
}
