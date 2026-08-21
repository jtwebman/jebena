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
}
