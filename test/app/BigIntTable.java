import java.math.BigInteger;

/**
 * Prints a formatted table of n!, fib(n), and 2^n for n=0..25 using BigInteger for
 * exact arbitrary-precision values and String.format for right-justified columns,
 * then a few larger values. Exercises BigInteger add/multiply/pow/valueOf/toString,
 * String.format width/%d/%s, and loops end-to-end.
 */
public class BigIntTable {
    static BigInteger fib(int n) {
        BigInteger a = BigInteger.ZERO;
        BigInteger b = BigInteger.ONE;
        for (int i = 0; i < n; i++) {
            BigInteger t = a.add(b);
            a = b;
            b = t;
        }
        return a;
    }

    static BigInteger factorial(int n) {
        BigInteger f = BigInteger.ONE;
        for (int i = 2; i <= n; i++) {
            f = f.multiply(BigInteger.valueOf(i));
        }
        return f;
    }

    public static void main(String[] args) {
        System.out.println(String.format("%3s %27s %22s %22s", "n", "n!", "fib(n)", "2^n"));
        BigInteger fact = BigInteger.ONE;
        BigInteger two = BigInteger.valueOf(2);
        for (int n = 0; n <= 25; n++) {
            if (n > 0) {
                fact = fact.multiply(BigInteger.valueOf(n));
            }
            System.out.println(String.format("%3d %27s %22s %22s",
                    Integer.valueOf(n), fact.toString(), fib(n).toString(), two.pow(n).toString()));
        }
        System.out.println("---");
        System.out.println("50!       = " + factorial(50));
        System.out.println("fib(100)  = " + fib(100));
        System.out.println("2^256     = " + two.pow(256));
        System.out.println("digits(100!) = " + factorial(100).toString().length());
    }
}
