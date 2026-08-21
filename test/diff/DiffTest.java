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
}
