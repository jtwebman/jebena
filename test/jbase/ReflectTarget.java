package jebena;

public class ReflectTarget {
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
