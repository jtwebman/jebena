package jebena;

public class ReflectTarget {
    @MyAnno(value = "f", num = 9)
    public int x;
    public String label;

    public ReflectTarget() {
        x = 0;
        label = "default";
    }

    public ReflectTarget(int x, String label) {
        this.x = x;
        this.label = label;
    }

    @MyAnno(value = "m", num = 7)
    public int addTo(int n) {
        return x + n;
    }

    public static int square(int n) {
        return n * n;
    }

    public String describe() {
        return label + ":" + x;
    }
}
