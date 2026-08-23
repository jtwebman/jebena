import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Scanner;

/**
 * A small stats report over a fixed whitespace-separated data set, exercising
 * three clean-room jbase classes end-to-end:
 *   - java.util.Scanner  : tokenize the input into ints
 *   - java.math.BigDecimal: exact running sum, spread, product, threshold compare
 *   - java.util.BitSet    : Sieve of Eratosthenes to classify inputs as prime
 * Output is plain text and must be byte-identical to real java.
 */
public class Stats {

    public static void main(String[] args) {
        String data = "17 3 42 8 23 5 16 11 4 29 2 37 19 6 13 31 25 40 7 12";

        Scanner sc = new Scanner(data);
        List<Integer> nums = new ArrayList<Integer>();
        while (sc.hasNextInt()) {
            nums.add(sc.nextInt());
        }
        sc.close();

        int count = nums.size();
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        BigDecimal sum = BigDecimal.valueOf(0L);
        for (int i = 0; i < count; i++) {
            int v = nums.get(i);
            sum = sum.add(BigDecimal.valueOf(v));
            if (v < min) {
                min = v;
            }
            if (v > max) {
                max = v;
            }
        }

        BigDecimal spread = BigDecimal.valueOf(max).subtract(BigDecimal.valueOf(min));
        BigDecimal product3 = BigDecimal.valueOf(nums.get(0))
                .multiply(BigDecimal.valueOf(nums.get(1)))
                .multiply(BigDecimal.valueOf(nums.get(2)));
        BigDecimal threshold = BigDecimal.valueOf(200L);
        boolean overThreshold = sum.compareTo(threshold) > 0;

        // Mean to 2 decimals without BigDecimal.divide: scale the exact sum by 100
        // and integer-divide (round half up) using long arithmetic.
        long sumLong = sum.longValue();
        long scaledMean = (sumLong * 100L + count / 2) / count;
        String meanStr = scaledMean / 100L + "." + twoDigits(scaledMean % 100L);

        // Sieve of Eratosthenes up to max, then classify each input.
        BitSet composite = new BitSet(max + 1);
        for (int p = 2; (long) p * p <= max; p++) {
            if (!composite.get(p)) {
                for (int m = p * p; m <= max; m += p) {
                    composite.set(m);
                }
            }
        }
        List<Integer> primes = new ArrayList<Integer>();
        for (int i = 0; i < count; i++) {
            int v = nums.get(i);
            if (v >= 2 && !composite.get(v)) {
                primes.add(v);
            }
        }
        sortAscending(primes);

        System.out.println("count=" + count);
        System.out.println("sum=" + sum.toPlainString());
        System.out.println("min=" + min + " max=" + max);
        System.out.println("spread=" + spread.toPlainString());
        System.out.println("mean=" + meanStr);
        System.out.println("product3=" + product3.toPlainString());
        System.out.println("sum>200=" + overThreshold);
        System.out.println("primeCount=" + primes.size());
        System.out.println("primes=" + join(primes));
    }

    private static String twoDigits(long n) {
        if (n < 10) {
            return "0" + n;
        }
        return "" + n;
    }

    private static void sortAscending(List<Integer> xs) {
        for (int i = 1; i < xs.size(); i++) {
            int key = xs.get(i);
            int j = i - 1;
            while (j >= 0 && xs.get(j) > key) {
                xs.set(j + 1, xs.get(j));
                j--;
            }
            xs.set(j + 1, key);
        }
    }

    private static String join(List<Integer> xs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < xs.size(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(xs.get(i));
        }
        return sb.toString();
    }
}
