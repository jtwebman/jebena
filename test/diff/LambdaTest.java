class Counter { int base; Counter(int b) { this.base = b; } int addTo(int x) { return base + x; } }
class Box { int v; Box(int v) { this.v = v; } int compute(int x) { IntOp f = y -> y + v; return f.apply(x); } int computeBi(int a, int b) { IntBiOp g = (p, q) -> p * q + v; return g.apply(a, b); } }
interface IntOp { int apply(int x); }
interface IntBiOp { int apply(int a, int b); }
public class LambdaTest {
    static int useLambda() { IntOp square = x -> x * x; IntOp inc = x -> x + 1; return square.apply(5) * 100 + inc.apply(10); }
    static int captureLambda() { int n = 100; int m = 7; IntOp f = x -> x + n * m; return f.apply(5); }
    static int biLambda() { IntBiOp add = (a, b) -> a + b; IntBiOp mul = (a, b) -> a * b; return add.apply(3, 4) * 100 + mul.apply(3, 4); }
    static int runnableTest() { int[] box = { 0 }; Runnable r = () -> box[0] = 42; r.run(); return box[0]; }
    static int negate(int x) { return -x; }
    static int methodRef() { IntOp neg = LambdaTest::negate; return neg.apply(5); }
    static int chainLambda() { int s = 0; IntOp[] ops = { x -> x + 1, x -> x * 2, x -> x - 3 }; for (IntOp op : ops) s += op.apply(10); return s; }
    static int nestedLambda() { int base = 1000; IntOp outer = x -> { IntOp inner = y -> y * 2; return inner.apply(x) + base; }; return outer.apply(21); }

    static int boundRef() { Counter c = new Counter(100); IntOp f = c::addTo; return f.apply(5) + f.apply(20); }
    static int thisCapture() { Box b = new Box(50); return b.compute(7); }
    static int thisCaptureBi() { Box b = new Box(1000); return b.computeBi(3, 4); }
    static int boundRefLoop() { Counter c = new Counter(10); IntOp f = c::addTo; int s = 0; for (int i = 0; i < 5; i++) s += f.apply(i); return s; }
}
