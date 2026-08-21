class Vec { int x, y; Vec(int x, int y) { this.x = x; this.y = y; } int dot(Vec o) { return x * o.x + y * o.y; } Vec add(Vec o) { return new Vec(x + o.x, y + o.y); } }
abstract class Shape { abstract int area(); int describe() { return area() * 2; } }
class Rect extends Shape { int w, h; Rect(int w, int h) { this.w = w; this.h = h; } int area() { return w * h; } }
class Circle extends Shape { int r; Circle(int r) { this.r = r; } int area() { return 3 * r * r; } int describe() { return super.describe() + r; } }
public class OopTest {
    static int poly() { Shape[] s = { new Rect(3, 4), new Circle(5), new Rect(2, 2) }; int t = 0; for (Shape sh : s) t += sh.describe(); return t; }
    static int vecs() { Vec a = new Vec(1, 2), b = new Vec(3, 4); Vec c = a.add(b); return c.dot(new Vec(2, 2)) + a.dot(b); }
    static int allocStress() { int s = 0; for (int i = 0; i < 2000; i++) { Vec v = new Vec(i, i + 1); s += v.dot(v) % 997; } return s; }
    static int listBuild() { Vec acc = new Vec(0, 0); for (int i = 0; i < 500; i++) acc = acc.add(new Vec(i % 7, i % 5)); return acc.dot(new Vec(1, 1)); }
}
