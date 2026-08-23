package java.text;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Clean-room implementation of a useful subset of {@code java.text.DecimalFormat}.
 *
 * <p>Supports pattern-driven formatting of {@code double} and {@code long} values:
 * integer grouping with {@code ','} (grouping size taken from the pattern),
 * a decimal point {@code '.'}, minimum integer digits from leading {@code '0'}
 * markers, minimum fraction digits from {@code '0'} and optional fraction digits
 * from {@code '#'} after the decimal point, and HALF_EVEN rounding of the
 * fractional part (the JDK default). Negative values are prefixed with {@code '-'}.
 *
 * <p>This is a deliberately narrow slice: it does not implement prefixes/suffixes,
 * percent/permille, currency, exponential notation, or custom symbols.
 */
public class DecimalFormat {

    private final int minIntDigits;
    private final int minFracDigits;
    private final int maxFracDigits;
    private final boolean grouping;
    private final int groupingSize;

    public DecimalFormat(String pattern) {
        if (pattern == null) {
            throw new NullPointerException("pattern");
        }

        int dot = pattern.indexOf('.');
        String intPart;
        String fracPart;
        if (dot < 0) {
            intPart = pattern;
            fracPart = "";
        } else {
            intPart = pattern.substring(0, dot);
            fracPart = pattern.substring(dot + 1);
        }

        // ---- integer part: count '0' markers and detect grouping ----
        int minInt = 0;
        int lastComma = -1;
        for (int i = 0; i < intPart.length(); i++) {
            char c = intPart.charAt(i);
            if (c == '0') {
                minInt++;
            } else if (c == ',') {
                lastComma = i;
            }
        }
        boolean group = lastComma >= 0;
        int gsize = 3;
        if (group) {
            // Grouping size = number of pattern chars after the last comma.
            gsize = intPart.length() - lastComma - 1;
            if (gsize <= 0) {
                gsize = 3;
            }
        }

        // ---- fraction part: '0' -> required, '#' -> optional ----
        int minFrac = 0;
        int maxFrac = 0;
        for (int i = 0; i < fracPart.length(); i++) {
            char c = fracPart.charAt(i);
            if (c == '0') {
                minFrac++;
                maxFrac++;
            } else if (c == '#') {
                maxFrac++;
            }
        }

        if (minInt == 0) {
            // At least one integer digit is always emitted for a zero value.
            minInt = 0; // JDK allows 0; a value of 0 with no '0' marker prints "".
        }

        this.minIntDigits = minInt;
        this.minFracDigits = minFrac;
        this.maxFracDigits = maxFrac;
        this.grouping = group;
        this.groupingSize = gsize;
    }

    public String format(long value) {
        return format(BigDecimal.valueOf(value), value < 0);
    }

    public String format(double value) {
        boolean neg = (value < 0.0) || (value == 0.0 && (1.0 / value) < 0.0);
        BigDecimal bd = BigDecimal.valueOf(value);
        return format(bd, neg);
    }

    private String format(BigDecimal bd, boolean negative) {
        // Work with the non-negative magnitude; sign is applied at the end.
        BigInteger unscaled = bd.unscaledValue();
        int scale = bd.scale();

        // Rescale the unscaled integer to exactly maxFracDigits places,
        // rounding HALF_EVEN when digits are dropped.
        BigInteger scaled = rescaleHalfEven(unscaled, scale, maxFracDigits);
        BigInteger mag = scaled.abs();

        String digits = mag.toString();
        // Ensure there is at least one integer digit: pad to length maxFrac + 1.
        int minLen = maxFracDigits + 1;
        if (digits.length() < minLen) {
            StringBuilder pad = new StringBuilder();
            for (int i = digits.length(); i < minLen; i++) {
                pad.append('0');
            }
            pad.append(digits);
            digits = pad.toString();
        }

        int intLen = digits.length() - maxFracDigits;
        String intDigits = digits.substring(0, intLen);
        String fracDigits = digits.substring(intLen);

        // Strip trailing zeros from the fraction down to the required minimum.
        int fracEnd = fracDigits.length();
        while (fracEnd > minFracDigits && fracDigits.charAt(fracEnd - 1) == '0') {
            fracEnd--;
        }
        fracDigits = fracDigits.substring(0, fracEnd);

        // Pad the integer part with leading zeros to the minimum width.
        if (intDigits.length() < minIntDigits) {
            StringBuilder pad = new StringBuilder();
            for (int i = intDigits.length(); i < minIntDigits; i++) {
                pad.append('0');
            }
            pad.append(intDigits);
            intDigits = pad.toString();
        }

        StringBuilder out = new StringBuilder();

        // A value that rounds to exactly zero should not carry a negative sign.
        boolean allZero = fracDigits.length() == 0 && isAllZero(intDigits);
        if (negative && !allZero) {
            out.append('-');
        }

        appendGrouped(out, intDigits);

        if (fracDigits.length() > 0) {
            out.append('.');
            out.append(fracDigits);
        }

        return out.toString();
    }

    private void appendGrouped(StringBuilder out, String intDigits) {
        int len = intDigits.length();
        if (!grouping || groupingSize <= 0 || len <= groupingSize) {
            out.append(intDigits);
            return;
        }
        int first = len % groupingSize;
        if (first == 0) {
            first = groupingSize;
        }
        out.append(intDigits.substring(0, first));
        for (int i = first; i < len; i += groupingSize) {
            out.append(',');
            out.append(intDigits.substring(i, i + groupingSize));
        }
    }

    private static boolean isAllZero(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) != '0') {
                return false;
            }
        }
        return true;
    }

    /**
     * Return the unscaled integer of {@code value} (given by {@code unscaled} and
     * {@code scale}) rescaled to {@code newScale}, using HALF_EVEN rounding when
     * fractional digits must be dropped.
     */
    private static BigInteger rescaleHalfEven(BigInteger unscaled, int scale, int newScale) {
        if (newScale >= scale) {
            int diff = newScale - scale;
            if (diff == 0) {
                return unscaled;
            }
            return unscaled.multiply(BigInteger.TEN.pow(diff));
        }

        int drop = scale - newScale;
        BigInteger divisor = BigInteger.TEN.pow(drop);
        BigInteger q = unscaled.divide(divisor);      // truncated toward zero
        BigInteger r = unscaled.remainder(divisor);   // sign follows dividend
        if (r.signum() == 0) {
            return q;
        }

        BigInteger twiceR = r.abs().multiply(BigInteger.TWO);
        int cmp = twiceR.compareTo(divisor);
        boolean roundAway;
        if (cmp > 0) {
            roundAway = true;
        } else if (cmp < 0) {
            roundAway = false;
        } else {
            // Exactly halfway: round to the even neighbour.
            boolean qOdd = q.remainder(BigInteger.TWO).signum() != 0;
            roundAway = qOdd;
        }

        if (roundAway) {
            q = q.add(BigInteger.valueOf(unscaled.signum()));
        }
        return q;
    }
}
