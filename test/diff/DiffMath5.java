public class DiffMath5 {
    public static int addExactInt() {
        int a = Math.addExact(2000000000, 100000000);
        int b = Math.addExact(-5, 12);
        return a / 1000 + b; // 2100000 + 7
    }

    public static int addExactIntOverflow() {
        try {
            return Math.addExact(Integer.MAX_VALUE, 1);
        } catch (ArithmeticException e) {
            return -999;
        }
    }

    public static int addExactLong() {
        long r = Math.addExact(9000000000000000000L, 200000000000000000L);
        return (int) (r % 1000000L); // deterministic tail
    }

    public static int addExactLongOverflow() {
        try {
            long r = Math.addExact(Long.MAX_VALUE, 1L);
            return (int) r;
        } catch (ArithmeticException e) {
            return -999;
        }
    }

    public static int subtractExactInt() {
        int r = Math.subtractExact(100, 250);
        return r; // -150
    }

    public static int subtractExactIntOverflow() {
        try {
            return Math.subtractExact(Integer.MIN_VALUE, 1);
        } catch (ArithmeticException e) {
            return -999;
        }
    }

    public static int subtractExactLongOverflow() {
        try {
            long r = Math.subtractExact(Long.MIN_VALUE, 1L);
            return (int) r;
        } catch (ArithmeticException e) {
            return -999;
        }
    }

    public static int multiplyExactInt() {
        int r = Math.multiplyExact(46340, 46340);
        return r; // 2147395600, fits
    }

    public static int multiplyExactIntOverflow() {
        try {
            return Math.multiplyExact(Integer.MAX_VALUE, 2);
        } catch (ArithmeticException e) {
            return -999;
        }
    }

    public static int multiplyExactLong() {
        long r = Math.multiplyExact(3000000000L, 3L);
        return (int) (r % 1000000L); // 9000000000 -> 0
    }

    public static int multiplyExactLongOverflow() {
        try {
            long r = Math.multiplyExact(Long.MAX_VALUE, 2L);
            return (int) r;
        } catch (ArithmeticException e) {
            return -999;
        }
    }

    public static int toIntExactNormal() {
        return Math.toIntExact(123456789L);
    }

    public static int toIntExactOverflow() {
        try {
            return Math.toIntExact(5000000000L);
        } catch (ArithmeticException e) {
            return -999;
        }
    }

    public static int negateExactNormal() {
        return Math.negateExact(12345);
    }

    public static int negateExactOverflow() {
        try {
            return Math.negateExact(Integer.MIN_VALUE);
        } catch (ArithmeticException e) {
            return -999;
        }
    }

    public static int negateExactLongOverflow() {
        try {
            long r = Math.negateExact(Long.MIN_VALUE);
            return (int) r;
        } catch (ArithmeticException e) {
            return -999;
        }
    }

    public static int incrementExactOverflow() {
        try {
            return Math.incrementExact(Integer.MAX_VALUE);
        } catch (ArithmeticException e) {
            return -999;
        }
    }

    public static int decrementExactOverflow() {
        try {
            return Math.decrementExact(Integer.MIN_VALUE);
        } catch (ArithmeticException e) {
            return -999;
        }
    }

    public static int incDecNormal() {
        int i = Math.incrementExact(41);
        int d = Math.decrementExact(100);
        long li = Math.incrementExact(999999999999L);
        long ld = Math.decrementExact(-5L);
        return i * 1000 + d + (int) (li % 1000L) + (int) ld;
        // 42000 + 99 + 0 (999...1000000000000%1000=1000000000000... compute)
    }
}
