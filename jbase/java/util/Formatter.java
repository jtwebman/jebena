package java.util;

/**
 * Clean-room java.util.Formatter supporting the common printf-style
 * conversions needed by String.format. The grammar handled is
 *
 *     %[flags][width][.precision]conversion
 *
 * with flags '-' (left-justify), '0' (zero-pad), '+' (leading plus),
 * ' ' (leading space); width is a minimum field width; precision bounds the
 * fraction digits of %f and the length of %s. Conversions: s d x X o f c b
 * plus the literals %% and %n. Formatting appends to an internal StringBuilder;
 * formatStatic(...) is the one-shot helper String.format delegates to.
 *
 * This is pure Java with no native methods and no synchronization.
 */
public final class Formatter {
    private final StringBuilder out;

    public Formatter() {
        out = new StringBuilder();
    }

    public Formatter format(String format, Object... args) {
        out.append(formatStatic(format, args));
        return this;
    }

    public String toString() {
        return out.toString();
    }

    /**
     * Format {@code format} against {@code args} and return the result. This is
     * the entry point String.format is expected to call.
     */
    public static String formatStatic(String format, Object[] args) {
        if (format == null) {
            throw new IllegalArgumentException("format is null");
        }
        StringBuilder sb = new StringBuilder();
        int len = format.length();
        int argIndex = 0;
        int i = 0;
        while (i < len) {
            char c = format.charAt(i);
            if (c != '%') {
                sb.append(c);
                i++;
                continue;
            }
            // c == '%': parse a conversion specification.
            i++;
            if (i >= len) {
                throw new IllegalArgumentException("dangling %");
            }

            // flags
            boolean flagLeft = false;
            boolean flagZero = false;
            boolean flagPlus = false;
            boolean flagSpace = false;
            while (i < len) {
                char f = format.charAt(i);
                if (f == '-') {
                    flagLeft = true;
                } else if (f == '0') {
                    flagZero = true;
                } else if (f == '+') {
                    flagPlus = true;
                } else if (f == ' ') {
                    flagSpace = true;
                } else if (f == '#' || f == ',' || f == '(') {
                    // accepted but not acted upon
                } else {
                    break;
                }
                i++;
            }

            // width
            int width = -1;
            while (i < len && format.charAt(i) >= '0' && format.charAt(i) <= '9') {
                if (width < 0) {
                    width = 0;
                }
                width = width * 10 + (format.charAt(i) - '0');
                i++;
            }

            // precision
            int precision = -1;
            if (i < len && format.charAt(i) == '.') {
                i++;
                precision = 0;
                while (i < len && format.charAt(i) >= '0' && format.charAt(i) <= '9') {
                    precision = precision * 10 + (format.charAt(i) - '0');
                    i++;
                }
            }

            if (i >= len) {
                throw new IllegalArgumentException("missing conversion");
            }
            char conv = format.charAt(i);
            i++;

            if (conv == '%') {
                sb.append('%');
                continue;
            }
            if (conv == 'n') {
                sb.append('\n');
                continue;
            }

            Object arg = (args == null || argIndex >= args.length) ? null : args[argIndex];
            argIndex++;

            String body;
            String sign = "";
            boolean numeric = false;

            if (conv == 's' || conv == 'S') {
                body = String.valueOf(arg);
                if (precision >= 0 && body.length() > precision) {
                    body = body.substring(0, precision);
                }
                if (conv == 'S') {
                    body = body.toUpperCase();
                }
            } else if (conv == 'b' || conv == 'B') {
                if (arg == null) {
                    body = "false";
                } else if (arg instanceof Boolean) {
                    body = ((Boolean) arg).booleanValue() ? "true" : "false";
                } else {
                    body = "true";
                }
                if (precision >= 0 && body.length() > precision) {
                    body = body.substring(0, precision);
                }
                if (conv == 'B') {
                    body = body.toUpperCase();
                }
            } else if (conv == 'c' || conv == 'C') {
                char ch;
                if (arg instanceof Character) {
                    ch = ((Character) arg).charValue();
                } else if (arg instanceof Integer) {
                    ch = (char) ((Integer) arg).intValue();
                } else {
                    throw new IllegalArgumentException("%c expects Character/Integer");
                }
                body = String.valueOf(ch);
                if (conv == 'C') {
                    body = body.toUpperCase();
                }
            } else if (conv == 'd') {
                numeric = true;
                long v = longValueOf(arg);
                boolean neg = v < 0;
                String digits = neg ? unsignedDecimalOfNegated(v) : String.valueOf(v);
                if (neg) {
                    sign = "-";
                } else if (flagPlus) {
                    sign = "+";
                } else if (flagSpace) {
                    sign = " ";
                }
                body = digits;
            } else if (conv == 'x' || conv == 'X') {
                numeric = true;
                boolean isLong = arg instanceof Long;
                long v = longValueOf(arg);
                body = toRadixPow2(v, 4, isLong);
                if (conv == 'X') {
                    body = body.toUpperCase();
                }
            } else if (conv == 'o') {
                numeric = true;
                boolean isLong = arg instanceof Long;
                long v = longValueOf(arg);
                body = toRadixPow2(v, 3, isLong);
            } else if (conv == 'f') {
                numeric = true;
                double d = doubleValueOf(arg);
                int p = (precision < 0) ? 6 : precision;
                boolean neg = false;
                if (d != d) {
                    body = "NaN";
                } else if (isInfinite(d)) {
                    neg = d < 0;
                    body = "Infinity";
                } else {
                    neg = (d < 0) || (d == 0.0 && 1.0 / d < 0);
                    body = formatFixed(d < 0 ? -d : d, p);
                }
                if (neg) {
                    sign = "-";
                } else if (flagPlus) {
                    sign = "+";
                } else if (flagSpace) {
                    sign = " ";
                }
            } else {
                throw new IllegalArgumentException("unsupported conversion: " + conv);
            }

            sb.append(pad(sign, body, width, flagLeft, flagZero && numeric));
        }
        return sb.toString();
    }

    // ---- helpers -------------------------------------------------------

    private static long longValueOf(Object arg) {
        if (arg instanceof Long) {
            return ((Long) arg).longValue();
        }
        if (arg instanceof Integer) {
            return ((Integer) arg).longValue();
        }
        if (arg instanceof Short) {
            return ((Short) arg).longValue();
        }
        if (arg instanceof Byte) {
            return ((Byte) arg).longValue();
        }
        throw new IllegalArgumentException("expected an integral argument");
    }

    private static double doubleValueOf(Object arg) {
        if (arg instanceof Double) {
            return ((Double) arg).doubleValue();
        }
        if (arg instanceof Float) {
            return ((Float) arg).doubleValue();
        }
        if (arg instanceof Integer) {
            return ((Integer) arg).doubleValue();
        }
        if (arg instanceof Long) {
            return ((Long) arg).doubleValue();
        }
        throw new IllegalArgumentException("expected a floating-point argument");
    }

    private static boolean isInfinite(double d) {
        return d != 0.0 && d == d && (d + d) == d;
    }

    /** Decimal digits of a negative long's magnitude, overflow-safe. */
    private static String unsignedDecimalOfNegated(long neg) {
        // neg <= 0; build magnitude digit by digit to survive Long.MIN_VALUE.
        if (neg == 0) {
            return "0";
        }
        StringBuilder rev = new StringBuilder();
        long v = neg;
        while (v != 0) {
            int digit = (int) -(v % 10); // 0..9
            rev.append((char) ('0' + digit));
            v = v / 10;
        }
        return rev.reverse().toString();
    }

    /** Unsigned power-of-two radix rendering (shift: 4 hex, 3 octal, 1 binary). */
    private static String toRadixPow2(long value, int shift, boolean isLong) {
        if (!isLong) {
            value = value & 0xFFFFFFFFL;
        }
        if (value == 0) {
            return "0";
        }
        long mask = (1L << shift) - 1;
        String digits = "0123456789abcdef";
        StringBuilder rev = new StringBuilder();
        long v = value;
        while (v != 0) {
            rev.append(digits.charAt((int) (v & mask)));
            v = v >>> shift;
        }
        return rev.reverse().toString();
    }

    /** Fixed-point rendering of a non-negative value with p fraction digits, half-up. */
    private static String formatFixed(double value, int p) {
        long pow = 1;
        for (int k = 0; k < p; k++) {
            pow = pow * 10;
        }
        double scaled = value * (double) pow;
        long rounded = (long) Math.floor(scaled + 0.5);
        long intPart = rounded / pow;
        long fracPart = rounded - intPart * pow;
        if (p == 0) {
            return String.valueOf(intPart);
        }
        String frac = String.valueOf(fracPart);
        StringBuilder sb = new StringBuilder();
        sb.append(String.valueOf(intPart));
        sb.append('.');
        for (int k = frac.length(); k < p; k++) {
            sb.append('0');
        }
        sb.append(frac);
        return sb.toString();
    }

    private static String pad(String sign, String body, int width, boolean left, boolean zero) {
        int total = sign.length() + body.length();
        if (width < 0 || total >= width) {
            return sign + body;
        }
        int padCount = width - total;
        StringBuilder sb = new StringBuilder();
        if (left) {
            sb.append(sign);
            sb.append(body);
            for (int k = 0; k < padCount; k++) {
                sb.append(' ');
            }
        } else if (zero) {
            sb.append(sign);
            for (int k = 0; k < padCount; k++) {
                sb.append('0');
            }
            sb.append(body);
        } else {
            for (int k = 0; k < padCount; k++) {
                sb.append(' ');
            }
            sb.append(sign);
            sb.append(body);
        }
        return sb.toString();
    }
}
