// Diverse pure-int/no-arg methods for differential testing Jebena vs real java.
public class DiffTest {
    static int arith() { int x = 7; return ((x * x * x) - 2 * x + 100) / 3 % 17; }
    static int loops() { int s = 0; for (int i = 0; i < 50; i++) for (int j = 0; j < i; j++) s += (i * j) % 7; return s; }
    static int rec() { return fib(24); }
    static int fib(int n) { return n < 2 ? n : fib(n - 1) + fib(n - 2); }
    static int longMath() { long a = 1; for (int i = 1; i <= 20; i++) a *= i; return (int) (a % 1000000007L); }
    static int doubleMath() { double d = 0; for (int i = 1; i <= 100; i++) d += 1.0 / i; return (int) (d * 1000000); }
    static int floatMath() { float f = 1.0f; for (int i = 0; i < 10; i++) f = f * 1.5f - 0.3f; return (int) (f * 1000); }
    static int bits() { int x = 0xABCD; return ((x << 3) ^ (x >> 2)) | (x & 0x0F0F); }
    static int arrays() { int[] a = new int[64]; for (int i = 0; i < 64; i++) a[i] = (i * i) % 251; int s = 0; for (int v : a) s += v; return s; }
    static int sw() { int s = 0; for (int i = 0; i < 30; i++) { switch (i % 5) { case 0: s += 1; break; case 1: s += 10; break; case 2: s += 100; break; case 3: s -= 5; break; default: s += 2; } } return s; }
    static int gcd() { return g(1071, 462); }
    static int g(int a, int b) { return b == 0 ? a : g(b, a % b); }
    static int conv() { int x = 1000000; long l = x; double d = l; float f = (float) d; return (int) f + (int) (l >> 1) + (int) d; }
    static int shifts() { long x = 1; for (int i = 0; i < 40; i++) x = (x << 1) | 1; return (int) (x % 99999); }
    static int exc() { int r = 0; try { for (int i = 0; i < 10; i++) { if (i == 7) throw new RuntimeException(); r += i; } } catch (RuntimeException e) { r += 1000; } finally { r += 1; } return r; }
    static int idivEdge() { int a = Integer.MIN_VALUE; int b = -1; return a / b + a % b; }
    static int overflow() { int x = 2000000000; return x + x + x; }

    static int negMod() { return (-17 % 5) * 100 + (17 % -5) + (-17 % -5); }
    static int longEdge() { return (int) (Long.MAX_VALUE + 1) + (int) (Long.MIN_VALUE - 1); }
    static int shiftBig() { return (1 << 35) + (int) (1L << 67) + (-8 >>> 1); }
    static int dSpecial() { double inf = 1.0 / 0.0, nan = 0.0 / 0.0; return (int) inf + (int) nan + (int) (-1.0 / 0.0); }
    static int fSpecial() { float inf = 1.0f / 0.0f; return (int) inf + (int) (0.0f / 0.0f) + (int) (-inf); }
    static int deepRec() { return sumTo(1000); }
    static int sumTo(int n) { return n == 0 ? 0 : n + sumTo(n - 1); }
    static int charMath() { char c = 'A'; c += 5; return c + (int) 'Z' + (c > 'B' ? 7 : 0); }
    static int ternary() { int x = 5; return x > 3 ? (x > 4 ? 100 : 200) : 300; }
    static int d2lConv() { double d = 1.23456789e15; long l = (long) d; return (int) (l % 1000000); }
    static int mixArith() { long a = 5; int b = 3; double c = 2.5; return (int) (a * b + (long) (c * 4)) + (int) (a / b); }
    static int cmpChain() { int a = 3, b = 5, c = 3; int r = 0; if (a < b) r += 1; if (a <= c) r += 2; if (b > c) r += 4; if (a == c) r += 8; if (b != c) r += 16; return r; }
    static int lcmpTest() { long a = 100000000000L, b = 99999999999L; return (a > b ? 1 : 0) + (a < b ? 10 : 0) + (a == b ? 100 : 0); }
    static int multiArr() { int[][] m = new int[4][5]; for (int i = 0; i < 4; i++) for (int j = 0; j < 5; j++) m[i][j] = i * 5 + j; int s = 0; for (int i = 0; i < 4; i++) for (int j = 0; j < 5; j++) s += m[i][j] * (i + 1); return s; }
}
