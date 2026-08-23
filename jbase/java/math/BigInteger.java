package java.math;

/**
 * Clean-room arbitrary-precision integer.
 *
 * Representation is sign-magnitude, matching the OpenJDK invariant:
 *   - {@code signum} is -1, 0, or 1.
 *   - {@code mag} holds the magnitude in big-endian order, base 2^32 words,
 *     with no leading zero words. Zero is represented by an empty {@code mag}
 *     and {@code signum == 0}.
 *
 * Arithmetic uses schoolbook add/subtract/multiply and a base-2 long-division
 * loop. Correctness is the priority; the operand sizes exercised here are small
 * enough that the quadratic pieces are inexpensive.
 */
public class BigInteger extends Number implements Comparable<BigInteger> {

    private static final long MASK = 0xffffffffL;

    /** -1, 0, or 1. */
    private final int signum;

    /** Big-endian magnitude, base 2^32, no leading zero words. Empty for zero. */
    private final int[] mag;

    public static final BigInteger ZERO = new BigInteger(0, new int[0]);
    public static final BigInteger ONE = new BigInteger(1, new int[] { 1 });
    public static final BigInteger TWO = new BigInteger(1, new int[] { 2 });
    public static final BigInteger TEN = new BigInteger(1, new int[] { 10 });

    /** Trusted constructor. Strips leading zeros and normalizes sign of zero. */
    private BigInteger(int signum, int[] mag) {
        int[] m = stripLeadingZeros(mag);
        if (m.length == 0) {
            this.signum = 0;
            this.mag = m;
        } else {
            this.signum = signum;
            this.mag = m;
        }
    }

    // ---------------------------------------------------------------- parsing

    public BigInteger(String val) {
        if (val == null) {
            throw new NumberFormatException("null");
        }
        int len = val.length();
        if (len == 0) {
            throw new NumberFormatException("Zero length BigInteger");
        }
        int sign = 1;
        int cursor = 0;
        char first = val.charAt(0);
        if (first == '-') {
            sign = -1;
            cursor = 1;
        } else if (first == '+') {
            cursor = 1;
        }
        if (cursor == len) {
            throw new NumberFormatException("Zero length BigInteger");
        }
        int[] m = new int[0];
        for (int i = cursor; i < len; i++) {
            char c = val.charAt(i);
            if (c < '0' || c > '9') {
                throw new NumberFormatException("For input string: \"" + val + "\"");
            }
            int digit = c - '0';
            m = mulAddSmall(m, 10, digit);
        }
        m = stripLeadingZeros(m);
        if (m.length == 0) {
            this.signum = 0;
            this.mag = m;
        } else {
            this.signum = sign;
            this.mag = m;
        }
    }

    public static BigInteger valueOf(long val) {
        if (val == 0) {
            return ZERO;
        }
        int sign = val < 0 ? -1 : 1;
        // -val for Long.MIN_VALUE overflows to itself, whose unsigned value is
        // exactly the desired magnitude 2^63, so the bit pattern is correct.
        long magnitude = val < 0 ? -val : val;
        int high = (int) (magnitude >>> 32);
        int low = (int) magnitude;
        int[] m;
        if (high != 0) {
            m = new int[] { high, low };
        } else {
            m = new int[] { low };
        }
        return new BigInteger(sign, m);
    }

    // -------------------------------------------------------------- accessors

    public int signum() {
        return signum;
    }

    public BigInteger abs() {
        return signum >= 0 ? this : negate();
    }

    public BigInteger negate() {
        return new BigInteger(-signum, mag);
    }

    public BigInteger min(BigInteger val) {
        return compareTo(val) <= 0 ? this : val;
    }

    public BigInteger max(BigInteger val) {
        return compareTo(val) >= 0 ? this : val;
    }

    // -------------------------------------------------------------- add / sub

    public BigInteger add(BigInteger val) {
        if (val.signum == 0) {
            return this;
        }
        if (signum == 0) {
            return val;
        }
        if (signum == val.signum) {
            return new BigInteger(signum, addMag(mag, val.mag));
        }
        int cmp = compareMag(mag, val.mag);
        if (cmp == 0) {
            return ZERO;
        }
        if (cmp > 0) {
            return new BigInteger(signum, subMag(mag, val.mag));
        }
        return new BigInteger(val.signum, subMag(val.mag, mag));
    }

    public BigInteger subtract(BigInteger val) {
        if (val.signum == 0) {
            return this;
        }
        return add(new BigInteger(-val.signum, val.mag));
    }

    // ---------------------------------------------------------------- multiply

    public BigInteger multiply(BigInteger val) {
        if (signum == 0 || val.signum == 0) {
            return ZERO;
        }
        return new BigInteger(signum * val.signum, mulMag(mag, val.mag));
    }

    // ------------------------------------------------------- divide / modulo

    public BigInteger divide(BigInteger val) {
        if (val.signum == 0) {
            throw new ArithmeticException("BigInteger divide by zero");
        }
        if (signum == 0) {
            return ZERO;
        }
        int[][] qr = divideMag(mag, val.mag);
        return new BigInteger(signum * val.signum, qr[0]);
    }

    public BigInteger remainder(BigInteger val) {
        if (val.signum == 0) {
            throw new ArithmeticException("BigInteger divide by zero");
        }
        if (signum == 0) {
            return ZERO;
        }
        int[][] qr = divideMag(mag, val.mag);
        // Remainder takes the sign of the dividend.
        return new BigInteger(signum, qr[1]);
    }

    public BigInteger mod(BigInteger m) {
        if (m.signum <= 0) {
            throw new ArithmeticException("BigInteger: modulus not positive");
        }
        BigInteger r = remainder(m);
        return r.signum >= 0 ? r : r.add(m);
    }

    // ------------------------------------------------------------------- pow

    public BigInteger pow(int exponent) {
        if (exponent < 0) {
            throw new ArithmeticException("Negative exponent");
        }
        if (exponent == 0) {
            return ONE;
        }
        BigInteger base = this;
        BigInteger result = ONE;
        int e = exponent;
        while (e != 0) {
            if ((e & 1) != 0) {
                result = result.multiply(base);
            }
            e >>>= 1;
            if (e != 0) {
                base = base.multiply(base);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------- gcd

    public BigInteger gcd(BigInteger val) {
        BigInteger a = this.abs();
        BigInteger b = val.abs();
        while (b.signum != 0) {
            BigInteger r = a.remainder(b);
            a = b;
            b = r;
        }
        return a;
    }

    // ------------------------------------------------------------- comparison

    public int compareTo(BigInteger val) {
        if (signum != val.signum) {
            return signum > val.signum ? 1 : -1;
        }
        if (signum == 0) {
            return 0;
        }
        int cmp = compareMag(mag, val.mag);
        return signum > 0 ? cmp : -cmp;
    }

    public boolean equals(Object x) {
        if (x == this) {
            return true;
        }
        if (!(x instanceof BigInteger)) {
            return false;
        }
        BigInteger other = (BigInteger) x;
        if (signum != other.signum) {
            return false;
        }
        if (mag.length != other.mag.length) {
            return false;
        }
        for (int i = 0; i < mag.length; i++) {
            if (mag[i] != other.mag[i]) {
                return false;
            }
        }
        return true;
    }

    public int hashCode() {
        int hashCode = 0;
        for (int i = 0; i < mag.length; i++) {
            hashCode = (int) (31 * hashCode + (mag[i] & 0xffffffffL));
        }
        return hashCode * signum;
    }

    // ------------------------------------------------------- primitive views

    public int intValue() {
        return getInt(0);
    }

    public long longValue() {
        return ((long) getInt(1) << 32) | (getInt(0) & MASK);
    }

    public float floatValue() {
        return (float) doubleValue();
    }

    public double doubleValue() {
        // Sufficient for the supported API; not part of the differential set.
        double d = 0.0;
        for (int i = 0; i < mag.length; i++) {
            d = d * 4294967296.0 + (mag[i] & MASK);
        }
        return signum < 0 ? -d : d;
    }

    /** Two's-complement 32-bit word n (0 = least significant), matching OpenJDK. */
    private int getInt(int n) {
        if (n < 0) {
            return 0;
        }
        if (n >= mag.length) {
            return signum < 0 ? -1 : 0;
        }
        int magInt = mag[mag.length - n - 1];
        if (signum >= 0) {
            return magInt;
        }
        return n <= firstNonzeroIntNum() ? -magInt : ~magInt;
    }

    /** LSB-index of the first nonzero magnitude word. */
    private int firstNonzeroIntNum() {
        int mlen = mag.length;
        int i;
        for (i = mlen - 1; i >= 0 && mag[i] == 0; i--) {
            // seek from least significant word upward
        }
        return mlen - i - 1;
    }

    // --------------------------------------------------------------- toString

    public String toString() {
        if (signum == 0) {
            return "0";
        }
        // Emit decimal in 9-digit chunks by repeatedly dividing by 10^9.
        int[] q = mag;
        int[] chunks = new int[mag.length * 2 + 2];
        int n = 0;
        while (q.length != 0) {
            Object[] dr = divModSmall(q, 1000000000);
            q = (int[]) dr[0];
            chunks[n++] = ((Integer) dr[1]).intValue();
        }
        StringBuilder sb = new StringBuilder();
        if (signum < 0) {
            sb.append('-');
        }
        // Most significant chunk without padding.
        sb.append(Integer.toString(chunks[n - 1]));
        for (int i = n - 2; i >= 0; i--) {
            String s = Integer.toString(chunks[i]);
            for (int pad = s.length(); pad < 9; pad++) {
                sb.append('0');
            }
            sb.append(s);
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- shifts

    public BigInteger shiftLeft(int n) {
        if (n == 0 || signum == 0) {
            return this;
        }
        if (n < 0) {
            if (n == Integer.MIN_VALUE) {
                throw new ArithmeticException("Shift distance of Integer.MIN_VALUE not supported.");
            }
            return shiftRight(-n);
        }
        return new BigInteger(signum, shiftLeftMag(mag, n));
    }

    public BigInteger shiftRight(int n) {
        if (n == 0 || signum == 0) {
            return this;
        }
        if (n < 0) {
            if (n == Integer.MIN_VALUE) {
                throw new ArithmeticException("Shift distance of Integer.MIN_VALUE not supported.");
            }
            return shiftLeft(-n);
        }
        if (signum > 0) {
            int[] r = shiftRightMag(mag, n);
            return r.length == 0 ? ZERO : new BigInteger(1, r);
        }
        // Negative: arithmetic shift rounds toward negative infinity, i.e. if
        // any set bit is shifted out the magnitude is incremented.
        int[] r = shiftRightMag(mag, n);
        boolean lost = bitsShiftedOut(mag, n);
        if (lost) {
            r = addMag(r, new int[] { 1 });
        }
        return r.length == 0 ? ZERO : new BigInteger(-1, r);
    }

    // -------------------------------------------------------- bitwise (2's comp)

    public BigInteger and(BigInteger val) {
        int n = Math.max(mag.length, val.mag.length) + 1;
        int[] result = new int[n];
        for (int i = 0; i < n; i++) {
            result[i] = getInt(n - 1 - i) & val.getInt(n - 1 - i);
        }
        return fromTwos(result);
    }

    public BigInteger or(BigInteger val) {
        int n = Math.max(mag.length, val.mag.length) + 1;
        int[] result = new int[n];
        for (int i = 0; i < n; i++) {
            result[i] = getInt(n - 1 - i) | val.getInt(n - 1 - i);
        }
        return fromTwos(result);
    }

    public BigInteger xor(BigInteger val) {
        int n = Math.max(mag.length, val.mag.length) + 1;
        int[] result = new int[n];
        for (int i = 0; i < n; i++) {
            result[i] = getInt(n - 1 - i) ^ val.getInt(n - 1 - i);
        }
        return fromTwos(result);
    }

    public BigInteger not() {
        // ~x == -(x + 1)
        return add(ONE).negate();
    }

    // ------------------------------------------------------------- single bits

    public boolean testBit(int n) {
        if (n < 0) {
            throw new ArithmeticException("Negative bit address");
        }
        return (getInt(n >>> 5) & (1 << (n & 31))) != 0;
    }

    public BigInteger setBit(int n) {
        return bitOp(n, 0);
    }

    public BigInteger clearBit(int n) {
        return bitOp(n, 1);
    }

    public BigInteger flipBit(int n) {
        return bitOp(n, 2);
    }

    private BigInteger bitOp(int n, int op) {
        if (n < 0) {
            throw new ArithmeticException("Negative bit address");
        }
        int word = n >>> 5;
        int bit = n & 31;
        int len = Math.max(mag.length, word + 1) + 1;
        int[] result = new int[len];
        for (int i = 0; i < len; i++) {
            result[i] = getInt(len - 1 - i);
        }
        int idx = len - 1 - word;
        if (op == 0) {
            result[idx] |= (1 << bit);
        } else if (op == 1) {
            result[idx] &= ~(1 << bit);
        } else {
            result[idx] ^= (1 << bit);
        }
        return fromTwos(result);
    }

    // ---------------------------------------------------------- bit counts

    public int bitLength() {
        if (signum == 0) {
            return 0;
        }
        int n = bitLength(mag);
        if (signum < 0 && popcountMag(mag) == 1) {
            // Negative powers of two need one fewer bit in two's complement.
            n -= 1;
        }
        return n;
    }

    public int bitCount() {
        if (signum >= 0) {
            return popcountMag(mag);
        }
        // For negative x with magnitude m, the two's-complement bits differing
        // from the sign bit equal popcount(m - 1).
        return popcountMag(subMag(mag, new int[] { 1 }));
    }

    // -------------------------------------------------------------- modPow etc.

    public BigInteger modPow(BigInteger exponent, BigInteger m) {
        if (m.signum <= 0) {
            throw new ArithmeticException("BigInteger: modulus not positive");
        }
        if (m.equals(ONE)) {
            return ZERO;
        }
        BigInteger base;
        BigInteger exp = exponent;
        if (exponent.signum < 0) {
            base = this.modInverse(m);
            exp = exponent.negate();
        } else {
            base = this.mod(m);
        }
        BigInteger result = ONE;
        BigInteger b = base;
        int bl = exp.bitLength();
        for (int i = 0; i < bl; i++) {
            if (exp.testBit(i)) {
                result = result.multiply(b).mod(m);
            }
            b = b.multiply(b).mod(m);
        }
        return result;
    }

    public BigInteger modInverse(BigInteger m) {
        if (m.signum != 1) {
            throw new ArithmeticException("BigInteger: modulus not positive");
        }
        if (m.equals(ONE)) {
            return ZERO;
        }
        BigInteger a = this.mod(m);
        BigInteger[] eg = extGcd(a, m);
        if (!eg[0].equals(ONE)) {
            throw new ArithmeticException("BigInteger not invertible.");
        }
        return eg[1].mod(m);
    }

    private static BigInteger[] extGcd(BigInteger a, BigInteger b) {
        BigInteger oldR = a;
        BigInteger r = b;
        BigInteger oldS = ONE;
        BigInteger s = ZERO;
        while (r.signum != 0) {
            BigInteger q = oldR.divide(r);
            BigInteger tmp = oldR.subtract(q.multiply(r));
            oldR = r;
            r = tmp;
            tmp = oldS.subtract(q.multiply(s));
            oldS = s;
            s = tmp;
        }
        return new BigInteger[] { oldR, oldS };
    }

    // -------------------------------------------------------------------- sqrt

    public BigInteger sqrt() {
        if (signum < 0) {
            throw new ArithmeticException("negative BigInteger");
        }
        if (signum == 0) {
            return ZERO;
        }
        int bl = bitLength(mag);
        BigInteger x = ONE.shiftLeft((bl + 1) / 2);
        while (true) {
            BigInteger y = x.add(this.divide(x)).shiftRight(1);
            if (y.compareTo(x) >= 0) {
                break;
            }
            x = y;
        }
        return x;
    }

    // ------------------------------------------------------------- primality

    public boolean isProbablePrime(int certainty) {
        if (certainty <= 0) {
            return true;
        }
        BigInteger w = this.abs();
        if (w.equals(TWO)) {
            return true;
        }
        if (w.signum == 0 || w.equals(ONE) || !w.testBit(0)) {
            return false;
        }
        return millerRabin(w);
    }

    /** Deterministic Miller-Rabin over fixed strong bases; n is odd and > 2. */
    private static boolean millerRabin(BigInteger n) {
        BigInteger nm1 = n.subtract(ONE);
        int r = 0;
        BigInteger d = nm1;
        while (!d.testBit(0)) {
            d = d.shiftRight(1);
            r++;
        }
        int[] bases = new int[] { 2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37 };
        for (int bi = 0; bi < bases.length; bi++) {
            BigInteger a = valueOf(bases[bi]);
            if (a.compareTo(n) >= 0) {
                continue;
            }
            BigInteger x = a.modPow(d, n);
            if (x.equals(ONE) || x.equals(nm1)) {
                continue;
            }
            boolean composite = true;
            for (int i = 0; i < r - 1; i++) {
                x = x.multiply(x).mod(n);
                if (x.equals(nm1)) {
                    composite = false;
                    break;
                }
            }
            if (composite) {
                return false;
            }
        }
        return true;
    }

    // ================================================ magnitude helpers ====

    private static int[] stripLeadingZeros(int[] a) {
        int i = 0;
        while (i < a.length && a[i] == 0) {
            i++;
        }
        if (i == 0 && a.length != 0) {
            return a;
        }
        int[] r = new int[a.length - i];
        for (int j = 0; j < r.length; j++) {
            r[j] = a[i + j];
        }
        return r;
    }

    /** Compare two stripped magnitudes: -1, 0, 1. */
    private static int compareMag(int[] x, int[] y) {
        if (x.length != y.length) {
            return x.length > y.length ? 1 : -1;
        }
        for (int i = 0; i < x.length; i++) {
            long xi = x[i] & MASK;
            long yi = y[i] & MASK;
            if (xi != yi) {
                return xi > yi ? 1 : -1;
            }
        }
        return 0;
    }

    private static int[] addMag(int[] x, int[] y) {
        if (x.length < y.length) {
            int[] t = x;
            x = y;
            y = t;
        }
        int[] result = new int[x.length];
        long carry = 0;
        int i = x.length - 1;
        int j = y.length - 1;
        while (i >= 0) {
            long yv = j >= 0 ? (y[j] & MASK) : 0;
            long sum = (x[i] & MASK) + yv + carry;
            result[i] = (int) sum;
            carry = sum >>> 32;
            i--;
            j--;
        }
        if (carry != 0) {
            int[] bigger = new int[result.length + 1];
            bigger[0] = (int) carry;
            for (int k = 0; k < result.length; k++) {
                bigger[k + 1] = result[k];
            }
            return bigger;
        }
        return result;
    }

    /** big - little, requires magnitude(big) >= magnitude(little). */
    private static int[] subMag(int[] big, int[] little) {
        int[] result = new int[big.length];
        long borrow = 0;
        int i = big.length - 1;
        int j = little.length - 1;
        while (i >= 0) {
            long lv = j >= 0 ? (little[j] & MASK) : 0;
            long diff = (big[i] & MASK) - lv - borrow;
            result[i] = (int) diff;
            borrow = diff < 0 ? 1 : 0;
            i--;
            j--;
        }
        return stripLeadingZeros(result);
    }

    private static int[] mulMag(int[] x, int[] y) {
        int[] result = new int[x.length + y.length];
        for (int i = x.length - 1; i >= 0; i--) {
            long carry = 0;
            long xv = x[i] & MASK;
            for (int j = y.length - 1; j >= 0; j--) {
                long prod = xv * (y[j] & MASK) + (result[i + j + 1] & MASK) + carry;
                result[i + j + 1] = (int) prod;
                carry = prod >>> 32;
            }
            result[i] = (int) carry;
        }
        return stripLeadingZeros(result);
    }

    /** magnitude * mul + add, with mul and add small non-negative ints. */
    private static int[] mulAddSmall(int[] mag, int mul, int add) {
        long m = mul & MASK;
        long carry = add & MASK;
        int[] r = new int[mag.length];
        for (int i = mag.length - 1; i >= 0; i--) {
            long v = (mag[i] & MASK) * m + carry;
            r[i] = (int) v;
            carry = v >>> 32;
        }
        if (carry != 0) {
            int[] bigger = new int[r.length + 1];
            bigger[0] = (int) carry;
            for (int k = 0; k < r.length; k++) {
                bigger[k + 1] = r[k];
            }
            return stripLeadingZeros(bigger);
        }
        return stripLeadingZeros(r);
    }

    /** Bit length of a stripped magnitude. */
    private static int bitLength(int[] mag) {
        if (mag.length == 0) {
            return 0;
        }
        return (mag.length - 1) * 32 + bitLen32(mag[0]);
    }

    /** Number of significant bits in a nonzero 32-bit word. */
    private static int bitLen32(int x) {
        int n = 0;
        long v = x & MASK;
        while (v != 0) {
            v >>>= 1;
            n++;
        }
        return n;
    }

    private static boolean testBit(int[] mag, int i) {
        int word = i >>> 5;
        int bit = i & 31;
        int index = mag.length - 1 - word;
        if (index < 0) {
            return false;
        }
        return ((mag[index] >>> bit) & 1) != 0;
    }

    private static int[] shiftLeft1(int[] mag) {
        if (mag.length == 0) {
            return mag;
        }
        int[] r = new int[mag.length];
        long carry = 0;
        for (int i = mag.length - 1; i >= 0; i--) {
            long v = ((mag[i] & MASK) << 1) | carry;
            r[i] = (int) v;
            carry = v >>> 32;
        }
        if (carry != 0) {
            int[] bigger = new int[r.length + 1];
            bigger[0] = (int) carry;
            for (int k = 0; k < r.length; k++) {
                bigger[k + 1] = r[k];
            }
            return bigger;
        }
        return r;
    }

    /** Sets the least significant bit; the input's low bit is assumed clear. */
    private static int[] setLowBit(int[] mag) {
        if (mag.length == 0) {
            return new int[] { 1 };
        }
        int[] r = new int[mag.length];
        for (int k = 0; k < mag.length; k++) {
            r[k] = mag[k];
        }
        r[r.length - 1] |= 1;
        return r;
    }

    /**
     * Divides stripped magnitude {@code dividend} by stripped magnitude
     * {@code divisor} (nonzero). Returns {quotient, remainder}, both stripped,
     * via base-2 long division.
     */
    private static int[][] divideMag(int[] dividend, int[] divisor) {
        if (compareMag(dividend, divisor) < 0) {
            return new int[][] { new int[0], dividend };
        }
        int n = bitLength(dividend);
        int qwords = (n + 31) / 32;
        int[] quotient = new int[qwords];
        int[] rem = new int[0];
        for (int i = n - 1; i >= 0; i--) {
            rem = shiftLeft1(rem);
            if (testBit(dividend, i)) {
                rem = setLowBit(rem);
            }
            if (compareMag(rem, divisor) >= 0) {
                rem = subMag(rem, divisor);
                int word = i >>> 5;
                int bit = i & 31;
                quotient[qwords - 1 - word] |= (1 << bit);
            }
        }
        return new int[][] { stripLeadingZeros(quotient), stripLeadingZeros(rem) };
    }

    /**
     * Divides stripped magnitude by a positive int divisor.
     * Returns {quotient (int[]), remainder (Integer)}.
     */
    private static Object[] divModSmall(int[] mag, int divisor) {
        long d = divisor & MASK;
        int[] q = new int[mag.length];
        long rem = 0;
        for (int i = 0; i < mag.length; i++) {
            long cur = (rem << 32) | (mag[i] & MASK);
            q[i] = (int) (cur / d);
            rem = cur % d;
        }
        return new Object[] { stripLeadingZeros(q), Integer.valueOf((int) rem) };
    }

    /** Population count of a stripped magnitude. */
    private static int popcountMag(int[] mag) {
        int c = 0;
        for (int i = 0; i < mag.length; i++) {
            c += Integer.bitCount(mag[i]);
        }
        return c;
    }

    /** Left-shift a stripped magnitude by n >= 0 bits. Result is stripped. */
    private static int[] shiftLeftMag(int[] mag, int n) {
        int len = mag.length;
        if (len == 0 || n == 0) {
            return mag;
        }
        int wordShift = n >>> 5;
        int bitShift = n & 31;
        if (bitShift == 0) {
            int[] r = new int[len + wordShift];
            for (int i = 0; i < len; i++) {
                r[i] = mag[i];
            }
            return stripLeadingZeros(r);
        }
        int nBits2 = 32 - bitShift;
        int highBits = mag[0] >>> nBits2;
        int[] r = new int[len + wordShift + (highBits != 0 ? 1 : 0)];
        int idx = 0;
        if (highBits != 0) {
            r[idx++] = highBits;
        }
        for (int i = 0; i < len; i++) {
            int cur = mag[i] << bitShift;
            int next = (i + 1 < len) ? (mag[i + 1] >>> nBits2) : 0;
            r[idx++] = cur | next;
        }
        return stripLeadingZeros(r);
    }

    /** Right-shift a stripped magnitude by n >= 0 bits (toward zero). Stripped. */
    private static int[] shiftRightMag(int[] mag, int n) {
        int len = mag.length;
        int wordShift = n >>> 5;
        int bitShift = n & 31;
        if (wordShift >= len) {
            return new int[0];
        }
        int newLen = len - wordShift;
        if (bitShift == 0) {
            int[] r = new int[newLen];
            for (int i = 0; i < newLen; i++) {
                r[i] = mag[i];
            }
            return stripLeadingZeros(r);
        }
        int nBits2 = 32 - bitShift;
        int[] r = new int[newLen];
        r[0] = mag[0] >>> bitShift;
        for (int i = 1; i < newLen; i++) {
            r[i] = (mag[i] >>> bitShift) | (mag[i - 1] << nBits2);
        }
        return stripLeadingZeros(r);
    }

    /** True if any set bit of the magnitude falls within the low n bits. */
    private static boolean bitsShiftedOut(int[] mag, int n) {
        int len = mag.length;
        int wordShift = n >>> 5;
        int bitShift = n & 31;
        // Whole low words that are dropped.
        int fullFrom = len - wordShift;
        for (int i = len - 1; i >= fullFrom && i >= 0; i--) {
            if (mag[i] != 0) {
                return true;
            }
        }
        if (bitShift != 0) {
            int idx = len - wordShift - 1;
            if (idx >= 0 && (mag[idx] << (32 - bitShift)) != 0) {
                return true;
            }
        }
        return false;
    }

    /** Interpret a big-endian two's-complement array as a BigInteger. */
    private static BigInteger fromTwos(int[] words) {
        if (words.length == 0) {
            return ZERO;
        }
        if (words[0] < 0) {
            return new BigInteger(-1, negateTwos(words));
        }
        int[] mag = stripLeadingZeros(words);
        if (mag.length == 0) {
            return ZERO;
        }
        return new BigInteger(1, mag);
    }

    /** Magnitude of the negative value held by a two's-complement array. */
    private static int[] negateTwos(int[] a) {
        int n = a.length;
        int[] r = new int[n];
        long carry = 1;
        for (int i = n - 1; i >= 0; i--) {
            long v = (~a[i] & MASK) + carry;
            r[i] = (int) v;
            carry = v >>> 32;
        }
        return stripLeadingZeros(r);
    }
}
