package java.math;

/**
 * Arbitrary-precision signed decimal number.
 *
 * <p>The value is represented as an unscaled {@link BigInteger} together with a
 * 32-bit integer {@code scale}: the numeric value equals
 * {@code unscaledValue x 10^-scale}. A positive scale therefore denotes digits
 * to the right of the decimal point; a negative scale multiplies by a power of
 * ten.
 *
 * <p>Clean-room implementation from the public contract.
 */
public class BigDecimal extends Number implements Comparable<BigDecimal> {

    public static final BigDecimal ZERO = new BigDecimal(BigInteger.ZERO, 0);
    public static final BigDecimal ONE = new BigDecimal(BigInteger.ONE, 0);
    public static final BigDecimal TEN = new BigDecimal(BigInteger.TEN, 0);

    private final BigInteger intVal;
    private final int scale;

    // -------------------------------------------------------------- construct

    private BigDecimal(BigInteger unscaled, int scale) {
        this.intVal = unscaled;
        this.scale = scale;
    }

    public BigDecimal(String val) {
        if (val == null) {
            throw new NumberFormatException("null");
        }
        int len = val.length();
        if (len == 0) {
            throw new NumberFormatException("Zero length BigDecimal");
        }
        int cursor = 0;
        boolean negative = false;
        char first = val.charAt(0);
        if (first == '-') {
            negative = true;
            cursor = 1;
        } else if (first == '+') {
            cursor = 1;
        }

        StringBuilder digits = new StringBuilder();
        int fracDigits = 0;
        boolean sawDot = false;
        boolean sawDigit = false;
        int i = cursor;
        for (; i < len; i++) {
            char c = val.charAt(i);
            if (c >= '0' && c <= '9') {
                digits.append(c);
                sawDigit = true;
                if (sawDot) {
                    fracDigits++;
                }
            } else if (c == '.') {
                if (sawDot) {
                    throw new NumberFormatException("Multiple points: \"" + val + "\"");
                }
                sawDot = true;
            } else if (c == 'e' || c == 'E') {
                break;
            } else {
                throw new NumberFormatException("For input string: \"" + val + "\"");
            }
        }
        if (!sawDigit) {
            throw new NumberFormatException("No digits: \"" + val + "\"");
        }

        int exp = 0;
        if (i < len) {
            // consume exponent marker
            i++;
            if (i == len) {
                throw new NumberFormatException("No exponent digits: \"" + val + "\"");
            }
            boolean expNeg = false;
            char ec = val.charAt(i);
            if (ec == '-') {
                expNeg = true;
                i++;
            } else if (ec == '+') {
                i++;
            }
            if (i == len) {
                throw new NumberFormatException("No exponent digits: \"" + val + "\"");
            }
            long e = 0;
            for (; i < len; i++) {
                char c = val.charAt(i);
                if (c < '0' || c > '9') {
                    throw new NumberFormatException("For input string: \"" + val + "\"");
                }
                e = e * 10 + (c - '0');
            }
            exp = expNeg ? (int) -e : (int) e;
        }

        String allDigits = digits.toString();
        BigInteger unscaled;
        if (allDigits.length() == 0) {
            unscaled = BigInteger.ZERO;
        } else {
            unscaled = new BigInteger(allDigits);
        }
        if (negative && unscaled.signum() != 0) {
            unscaled = unscaled.negate();
        }
        // scale = fractional digit count minus the explicit exponent.
        this.intVal = unscaled;
        this.scale = fracDigits - exp;
    }

    // ----------------------------------------------------------- static build

    public static BigDecimal valueOf(long val) {
        return new BigDecimal(BigInteger.valueOf(val), 0);
    }

    public static BigDecimal valueOf(double val) {
        // Matches JDK: BigDecimal.valueOf(double) == new BigDecimal(Double.toString(val)).
        return new BigDecimal(Double.toString(val));
    }

    // ------------------------------------------------------------- accessors

    public int scale() {
        return scale;
    }

    public BigInteger unscaledValue() {
        return intVal;
    }

    public int signum() {
        return intVal.signum();
    }

    // ------------------------------------------------------------ arithmetic

    /** Rescale {@code v} up to the larger scale by padding with zero digits. */
    private static BigInteger rescaledUnscaled(BigDecimal v, int targetScale) {
        int diff = targetScale - v.scale;
        if (diff == 0) {
            return v.intVal;
        }
        // targetScale >= v.scale so diff > 0: multiply by 10^diff.
        BigInteger factor = BigInteger.TEN.pow(diff);
        return v.intVal.multiply(factor);
    }

    public BigDecimal add(BigDecimal augend) {
        int newScale = Math.max(this.scale, augend.scale);
        BigInteger a = rescaledUnscaled(this, newScale);
        BigInteger b = rescaledUnscaled(augend, newScale);
        return new BigDecimal(a.add(b), newScale);
    }

    public BigDecimal subtract(BigDecimal subtrahend) {
        int newScale = Math.max(this.scale, subtrahend.scale);
        BigInteger a = rescaledUnscaled(this, newScale);
        BigInteger b = rescaledUnscaled(subtrahend, newScale);
        return new BigDecimal(a.subtract(b), newScale);
    }

    public BigDecimal multiply(BigDecimal multiplicand) {
        BigInteger product = this.intVal.multiply(multiplicand.intVal);
        return new BigDecimal(product, this.scale + multiplicand.scale);
    }

    public BigDecimal negate() {
        return new BigDecimal(intVal.negate(), scale);
    }

    public BigDecimal abs() {
        return signum() < 0 ? negate() : this;
    }

    // --------------------------------------------------------------- divide

    public BigDecimal divide(BigDecimal divisor, int scale, RoundingMode mode) {
        if (divisor.signum() == 0) {
            throw new ArithmeticException("Division by zero");
        }
        // We want q such that (q * 10^-scale) approximates this/divisor.
        //   this/divisor = (thisUnscaled/divUnscaled) * 10^(divScale - thisScale)
        //   q = this/divisor * 10^scale
        //     = (thisUnscaled * 10^shift) / divUnscaled ,  shift = scale + divScale - thisScale
        int shift = scale + divisor.scale - this.scale;
        BigInteger dividend = this.intVal;
        BigInteger divisorInt = divisor.intVal;
        if (shift >= 0) {
            dividend = dividend.multiply(BigInteger.TEN.pow(shift));
        } else {
            divisorInt = divisorInt.multiply(BigInteger.TEN.pow(-shift));
        }
        BigInteger q = divideAndRound(dividend, divisorInt, mode);
        return new BigDecimal(q, scale);
    }

    /** Integer division of {@code dividend/divisor} with the given rounding. */
    private static BigInteger divideAndRound(BigInteger dividend, BigInteger divisor, RoundingMode mode) {
        BigInteger q = dividend.divide(divisor);      // truncated toward zero
        BigInteger r = dividend.remainder(divisor);   // sign of dividend
        if (r.signum() == 0) {
            return q;
        }
        // Sign of the exact quotient.
        int quotientSign = dividend.signum() * divisor.signum();

        boolean roundAway;
        if (mode == RoundingMode.DOWN) {
            roundAway = false;
        } else if (mode == RoundingMode.UP) {
            roundAway = true;
        } else if (mode == RoundingMode.CEILING) {
            roundAway = quotientSign > 0;
        } else if (mode == RoundingMode.FLOOR) {
            roundAway = quotientSign < 0;
        } else if (mode == RoundingMode.HALF_UP
                || mode == RoundingMode.HALF_DOWN
                || mode == RoundingMode.HALF_EVEN) {
            BigInteger twiceR = r.abs().multiply(BigInteger.TWO);
            int cmp = twiceR.compareTo(divisor.abs());
            if (cmp > 0) {
                roundAway = true;
            } else if (cmp < 0) {
                roundAway = false;
            } else if (mode == RoundingMode.HALF_UP) {
                roundAway = true;
            } else if (mode == RoundingMode.HALF_DOWN) {
                roundAway = false;
            } else {
                // HALF_EVEN: round away only if the truncated quotient is odd.
                roundAway = q.remainder(BigInteger.TWO).signum() != 0;
            }
        } else {
            throw new ArithmeticException("Unsupported rounding mode: " + mode);
        }

        if (roundAway) {
            q = q.add(BigInteger.valueOf(quotientSign));
        }
        return q;
    }

    // --------------------------------------------------------------- powers

    public BigDecimal pow(int n) {
        if (n < 0) {
            throw new ArithmeticException("Invalid operation: negative exponent");
        }
        if (n == 0) {
            return ONE;
        }
        BigDecimal result = ONE;
        for (int i = 0; i < n; i++) {
            result = result.multiply(this);
        }
        return result;
    }

    // ------------------------------------------------------- point movement

    public BigDecimal stripTrailingZeros() {
        if (intVal.signum() == 0) {
            return new BigDecimal(BigInteger.ZERO, 0);
        }
        BigInteger u = intVal;
        int s = scale;
        while (true) {
            BigInteger q = u.divide(BigInteger.TEN);
            BigInteger r = u.remainder(BigInteger.TEN);
            if (r.signum() != 0) {
                break;
            }
            u = q;
            s = s - 1;
        }
        return new BigDecimal(u, s);
    }

    public BigDecimal movePointLeft(int n) {
        int newScale = scale + n;
        if (newScale < 0) {
            // Absorb the deficit into the unscaled value.
            return new BigDecimal(intVal.multiply(BigInteger.TEN.pow(-newScale)), 0);
        }
        return new BigDecimal(intVal, newScale);
    }

    public BigDecimal movePointRight(int n) {
        int newScale = scale - n;
        if (newScale < 0) {
            return new BigDecimal(intVal.multiply(BigInteger.TEN.pow(-newScale)), 0);
        }
        return new BigDecimal(intVal, newScale);
    }

    // --------------------------------------------------------------- rescale

    public BigDecimal setScale(int newScale, RoundingMode roundingMode) {
        if (newScale >= this.scale) {
            // Increasing (or keeping) scale never loses information.
            int diff = newScale - this.scale;
            if (diff == 0) {
                return this;
            }
            BigInteger factor = BigInteger.TEN.pow(diff);
            return new BigDecimal(this.intVal.multiply(factor), newScale);
        }

        int drop = this.scale - newScale;
        BigInteger divisor = BigInteger.TEN.pow(drop);
        BigInteger q = this.intVal.divide(divisor);      // truncated toward zero
        BigInteger r = this.intVal.remainder(divisor);   // sign of dividend

        if (r.signum() == 0) {
            return new BigDecimal(q, newScale);
        }

        boolean roundAway;
        if (roundingMode == RoundingMode.DOWN) {
            roundAway = false;
        } else if (roundingMode == RoundingMode.UP) {
            roundAway = true;
        } else if (roundingMode == RoundingMode.HALF_UP) {
            // |r| * 2 >= divisor  ->  round away from zero.
            BigInteger twiceR = r.abs().multiply(BigInteger.TWO);
            roundAway = twiceR.compareTo(divisor) >= 0;
        } else {
            throw new ArithmeticException("Unsupported rounding mode: " + roundingMode);
        }

        if (roundAway) {
            q = q.add(BigInteger.valueOf(this.intVal.signum()));
        }
        return new BigDecimal(q, newScale);
    }

    // ------------------------------------------------------------- comparison

    public int compareTo(BigDecimal val) {
        // Fast path on signs.
        int s1 = this.signum();
        int s2 = val.signum();
        if (s1 != s2) {
            return s1 > s2 ? 1 : -1;
        }
        if (s1 == 0) {
            return 0;
        }
        int newScale = Math.max(this.scale, val.scale);
        BigInteger a = rescaledUnscaled(this, newScale);
        BigInteger b = rescaledUnscaled(val, newScale);
        return a.compareTo(b);
    }

    public boolean equals(Object x) {
        if (this == x) {
            return true;
        }
        if (!(x instanceof BigDecimal)) {
            return false;
        }
        BigDecimal other = (BigDecimal) x;
        return this.scale == other.scale && this.intVal.equals(other.intVal);
    }

    public int hashCode() {
        return 31 * intVal.hashCode() + scale;
    }

    // --------------------------------------------------------------- Number

    public int intValue() {
        return toBigInteger().intValue();
    }

    public long longValue() {
        return toBigInteger().longValue();
    }

    public float floatValue() {
        return (float) doubleValue();
    }

    public double doubleValue() {
        double u = intVal.doubleValue();
        if (scale == 0) {
            return u;
        }
        return u * Math.pow(10.0, -scale);
    }

    public BigInteger toBigInteger() {
        if (scale == 0) {
            return intVal;
        }
        if (scale > 0) {
            return intVal.divide(BigInteger.TEN.pow(scale));
        }
        return intVal.multiply(BigInteger.TEN.pow(-scale));
    }

    // ---------------------------------------------------------------- output

    public String toPlainString() {
        String mag = intVal.abs().toString();
        boolean neg = intVal.signum() < 0;
        StringBuilder sb = new StringBuilder();
        if (neg) {
            sb.append('-');
        }
        if (scale == 0) {
            sb.append(mag);
        } else if (scale < 0) {
            sb.append(mag);
            for (int i = 0; i < -scale; i++) {
                sb.append('0');
            }
        } else {
            int intLen = mag.length() - scale;
            if (intLen > 0) {
                sb.append(mag.substring(0, intLen));
                sb.append('.');
                sb.append(mag.substring(intLen));
            } else {
                sb.append("0.");
                for (int i = 0; i < -intLen; i++) {
                    sb.append('0');
                }
                sb.append(mag);
            }
        }
        return sb.toString();
    }

    public String toString() {
        // Minimal slice: emit the plain (non-scientific) form. This differs from
        // the JDK's scientific formatting for extreme scales but is exact for the
        // ordinary fixed-point values this slice is verified against.
        return toPlainString();
    }
}
