package cp;

public class Accumulator {
    private int sum;

    public void add(int x) {
        sum += x;
    }

    public int total() {
        return sum;
    }
}
