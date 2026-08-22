import java.math.BigInteger;

/**
 * Differential coverage for java.math.BigInteger: valueOf/String ctor, add/subtract/
 * multiply/divide/mod/remainder/pow/gcd/abs/negate/compareTo/signum, and toString of
 * large values (exercises the arbitrary-precision arithmetic + base-10 rendering).
 * Each case returns a deterministic int (intValue, toString length/char-sum, or a
 * compareTo) checked byte-for-byte vs real java.
 */
public class DiffBig {
    private static int hstr(BigInteger b) {
        String s = b.toString();
        int a = s.length() * 1000003;
        for (int i = 0; i < s.length(); i++) {
            a = a * 31 + s.charAt(i);
        }
        return a;
    }

    static int addSub() {
        BigInteger a = BigInteger.valueOf(1234567890123L);
        BigInteger b = BigInteger.valueOf(9876543210987L);
        return (int) a.add(b).subtract(BigInteger.valueOf(3)).longValue(); // 11111111101107
    }

    static int multiply() {
        BigInteger a = new BigInteger("123456789");
        BigInteger b = new BigInteger("987654321");
        return hstr(a.multiply(b)); // 121932631112635269
    }

    static int divMod() {
        BigInteger a = new BigInteger("1000000000000000000000");
        BigInteger b = new BigInteger("7");
        return hstr(a.divide(b)) ^ hstr(a.mod(b)) ^ hstr(a.remainder(b));
    }

    static int powBig() {
        // 2^128 is well beyond long; verify toString of the exact value
        return hstr(BigInteger.valueOf(2).pow(128));
    }

    static int factorial() {
        BigInteger f = BigInteger.ONE;
        for (int i = 1; i <= 30; i++) {
            f = f.multiply(BigInteger.valueOf(i));
        }
        return hstr(f); // 30! = 265252859812191058636308480000000
    }

    static int gcd() {
        return BigInteger.valueOf(462).gcd(BigInteger.valueOf(1071)).intValue(); // 21
    }

    static int compare() {
        BigInteger a = new BigInteger("100000000000000000000");
        BigInteger b = new BigInteger("100000000000000000001");
        return a.compareTo(b) * 100 + (b.compareTo(a) + 1) * 10 + (a.compareTo(a) + 2);
        // -1*100 + (1+1)*10 + (0+2) = -100 + 20 + 2 = -78
    }

    static int signNeg() {
        BigInteger a = new BigInteger("-42");
        return a.signum() * 1000 + a.abs().intValue() * 10 + (a.negate().signum() + 1);
        // -1*1000 + 42*10 + (1+1) = -1000 + 420 + 2 = -578
    }

    static int modArith() {
        // (7^13 mod 1000000) via repeated multiply+mod
        BigInteger base = BigInteger.valueOf(7);
        BigInteger m = BigInteger.valueOf(1000000);
        BigInteger acc = BigInteger.ONE;
        for (int i = 0; i < 13; i++) {
            acc = acc.multiply(base).mod(m);
        }
        return acc.intValue();
    }

    static int negDivide() {
        // Java BigInteger divide truncates toward zero; remainder keeps dividend sign
        BigInteger a = new BigInteger("-17");
        BigInteger b = new BigInteger("5");
        return a.divide(b).intValue() * 100 + (a.remainder(b).intValue() + 10);
        // -3*100 + (-2+10) = -300 + 8 = -292
    }
}
