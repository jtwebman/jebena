package cp;

public class Helper {
    private final int base;

    public Helper(int base) {
        this.base = base;
    }

    public int offsetBy(int d) {
        return base + d;
    }

    public static int square(int x) {
        return x * x;
    }

    public static int cube(int x) {
        return x * x * x;
    }
}
