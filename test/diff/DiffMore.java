import java.util.Arrays;
public class DiffMore {
    static int nanCmp() { double d = 0.0 / 0.0; return (d < 1.0 ? 1 : 0) + (d > 1.0 ? 10 : 0) + (d == d ? 100 : 0) + (d != d ? 1000 : 0); }
    static int nanCmpF() { float f = 0.0f / 0.0f; return (f <= 1.0f ? 1 : 0) + (f >= 1.0f ? 10 : 0) + (f < f ? 100 : 0); }
    static boolean f1(int[] c) { c[0] += 1; return false; }
    static boolean f2(int[] c) { c[0] += 100; return true; }
    static int shortCircuit() { int[] c = {0}; boolean b = f1(c) && f2(c); return (b ? 1 : 0) * 10000 + c[0]; }
    static boolean g1(int[] c) { c[0] += 1; return true; }
    static boolean g2(int[] c) { c[0] += 100; return false; }
    static int orCircuit() { int[] c = {0}; boolean b = g1(c) || g2(c); return (b ? 1 : 0) * 10000 + c[0]; }
    static int labeledBreak() { int s = 0; outer: for (int i = 0; i < 10; i++) { for (int j = 0; j < 10; j++) { if (i * j > 20) break outer; s += i * j; } } return s; }
    static int labeledCont() { int s = 0; outer: for (int i = 0; i < 5; i++) { for (int j = 0; j < 5; j++) { if (j == 2) continue outer; s += i * 10 + j; } } return s; }
    static int doWhile() { int i = 0, s = 0; do { s += i; i++; } while (i < 10); return s; }
    static boolean isEven(int n) { return n == 0 ? true : isOdd(n - 1); }
    static boolean isOdd(int n) { return n == 0 ? false : isEven(n - 1); }
    static int mutual() { return (isEven(30) ? 1 : 0) + (isOdd(17) ? 10 : 0); }
    static int ackermann(int m, int n) { return m == 0 ? n + 1 : (n == 0 ? ackermann(m - 1, 1) : ackermann(m - 1, ackermann(m, n - 1))); }
    static int ack() { return ackermann(2, 3) * 1000 + ackermann(3, 3); }
    static int prePost() { int i = 5; int a = i++; int b = ++i; int c = i--; int d = --i; return a * 1000 + b * 100 + c * 10 + d; }
    static int compound() { int x = 10; x += 5; x *= 2; x -= 3; x /= 2; x %= 7; x <<= 2; x >>= 1; x |= 1; x &= 0xF; x ^= 3; return x; }
    static int longDivEdge() { long a = Long.MIN_VALUE; return (int) (a / -1L) + (int) (a % -1L); }
    static int negAbs() { int x = Integer.MIN_VALUE; return (x < 0 ? -x : x); }
    static int allConv() { int i = -5; long l = i; float f = i; double d = i; return (int) ((long) f + (long) d + (int) l + (long) (f + d)); }
    static int nestedExc() { int r = 0; try { try { throw new RuntimeException(); } finally { r += 1; } } catch (RuntimeException e) { r += 10; } return r; }
    static int rethrow() { int r = 0; try { try { throw new RuntimeException(); } catch (RuntimeException e) { r += 1; throw e; } } catch (RuntimeException e) { r += 10; } return r; }
    static int gcExc() { int s = 0; for (int i = 0; i < 500; i++) { try { int[] a = new int[10]; a[i % 10] = i; if (i % 50 == 0) throw new RuntimeException(); s += a[i % 10]; } catch (RuntimeException e) { s += 1; } } return s; }
    static int deepMutual() { return count(2000); }
    static int count(int n) { return n == 0 ? 0 : 1 + count(n - 1); }

    static int divZero() { int r = 0; try { r = 10 / 0; } catch (ArithmeticException e) { r = 99; } return r; }
    static int divZeroLong() { long r = 0; try { r = 10L / 0L; } catch (ArithmeticException e) { r = 77; } return (int) r; }
    static int npe() { int r = 0; int[] a = null; try { r = a.length; } catch (NullPointerException e) { r = 42; } return r; }
    static int aioobe() { int r = 0; int[] a = new int[3]; try { a[5] = 1; } catch (ArrayIndexOutOfBoundsException e) { r = 55; } return r; }
    static int aioobeNeg() { int r = 0; int[] a = new int[3]; try { r = a[-1]; } catch (ArrayIndexOutOfBoundsException e) { r = 66; } return r; }
    static int negArr() { int r = 0; try { int[] a = new int[-1]; r = a.length; } catch (NegativeArraySizeException e) { r = 88; } return r; }
    static int catchSuper() { int r = 0; try { int x = 1 / 0; if (x > 0) r = 1; } catch (RuntimeException e) { r = 7; } return r; }
    static int catchThrowable() { int r = 0; try { int[] a = null; r = a[0]; } catch (Throwable t) { r = 9; } return r; }
    static int finallyOnExc() { int r = 0; try { r = 1 / 0; } catch (ArithmeticException e) { r = 3; } finally { r += 10; } return r; }
    static int multiCatch() { int r = 0; try { int[] a = new int[2]; a[9] = 1; } catch (ArithmeticException | ArrayIndexOutOfBoundsException e) { r = 4; } return r; }
    static int gcArith() { int s = 0; for (int i = 0; i < 300; i++) { int[] junk = new int[8]; try { s += junk.length / (i % 7); } catch (ArithmeticException e) { s += 100; } } return s; }

    static int mAbs() { return Math.abs(-42) + (int) Math.abs(-7L) + (int) Math.abs(-3.5) * 10; }
    static int mMaxMin() { return Math.max(3, 8) * 100 + Math.min(3, 8) * 10 + (int) Math.max(1.5, 2.5); }
    static int mSqrt() { return (int) Math.sqrt(144.0) + (int) (Math.sqrt(2.0) * 1000); }
    static int mPow() { return (int) Math.pow(2, 10) + (int) Math.pow(3, 3); }
    static int mRound() { return (int) Math.round(3.5) + (int) Math.round(2.4) + (int) Math.round(-2.5) + Math.round(1.6f); }
    static int mFloorCeil() { return (int) Math.floor(3.7) + (int) Math.ceil(3.2) + (int) Math.floor(-1.5) + (int) Math.ceil(-1.5); }
    static int mHypot() { return (int) Math.hypot(3.0, 4.0) + (int) (Math.hypot(5.0, 12.0)); }
    static int mExp() { return (int) (Math.exp(1.0) * 1000) + (int) (Math.sin(0.0) * 1000) + (int) (Math.cos(0.0) * 1000); }
    static int mMixed() { double d = 0; for (int i = 1; i <= 20; i++) d += Math.sqrt(i) * Math.abs(i - 10); return (int) d; }

    static int copy() { int[] a = { 1, 2, 3, 4, 5 }; int[] b = new int[5]; System.arraycopy(a, 1, b, 0, 3); return b[0] * 10000 + b[1] * 1000 + b[2] * 100 + b[3] * 10 + b[4]; }
    static int copyOverlap() { int[] a = { 1, 2, 3, 4, 5, 6, 7, 8 }; System.arraycopy(a, 0, a, 2, 4); int s = 0; for (int v : a) s = s * 10 + v; return s; }
    static int copyGrow() { int[] a = { 5, 6, 7 }; for (int k = 0; k < 6; k++) { int[] b = new int[a.length * 2]; System.arraycopy(a, 0, b, 0, a.length); a = b; } int s = 0; for (int v : a) s += v; return s; }
    static int copyLong() { long[] a = { 10L, 20L, 30L }; long[] b = new long[3]; System.arraycopy(a, 0, b, 0, 3); return (int) (b[0] + b[1] + b[2]); }

    static int iBits() { int x = 0xF0F0F0F0; return Integer.bitCount(x) * 1000 + Integer.numberOfLeadingZeros(x) * 10 + Integer.numberOfTrailingZeros(x); }
    static int iBits2() { return Integer.highestOneBit(0x1234) + Integer.lowestOneBit(0x1234) + Integer.reverseBytes(0x12345678) / 65536; }
    static int iRotate() { int x = 0x12345678; return Integer.rotateLeft(x, 8) ^ Integer.rotateRight(x, 8); }
    static int iReverse() { return Integer.reverse(1) + Integer.reverse(-2147483648); }
    static int lBits() { long x = 0xF0F0F0F0F0F0F0F0L; return Long.bitCount(x) * 100 + Long.numberOfTrailingZeros(x); }
    static int iMinMax() { return Integer.max(-5, 3) * 100 + Integer.min(-5, 3) + Integer.signum(-99) * 10; }
    static int bitLoop() { int s = 0; for (int i = 1; i <= 1000; i++) s += Integer.bitCount(i) + Integer.numberOfTrailingZeros(i); return s; }

    static int sortTest() { int[] a = { 5, 3, 8, 1, 9, 2, 7, 4, 6, 0 }; Arrays.sort(a); int s = 0; for (int v : a) s = s * 10 + v; return s; }
    static int sortBig() { int[] a = new int[500]; int x = 12345; for (int i = 0; i < 500; i++) { x = (x * 1103515245 + 12345) & 0x7fffffff; a[i] = x % 1000; } Arrays.sort(a); int s = 0; for (int i = 1; i < 500; i++) if (a[i - 1] > a[i]) s++; return s * 100000 + a[0] + a[499]; }
    static int fillCopy() { int[] a = new int[6]; Arrays.fill(a, 7); int[] b = Arrays.copyOf(a, 10); int s = 0; for (int v : b) s += v; return s * 10 + b.length; }
    static int eqTest() { int[] a = { 1, 2, 3 }; int[] b = { 1, 2, 3 }; int[] c = { 1, 2, 4 }; return (Arrays.equals(a, b) ? 1 : 0) * 10 + (Arrays.equals(a, c) ? 1 : 0); }
}
