package java.util;

/**
 * Clean-room mutable accumulator for summary statistics over a stream of ints.
 * Tracks count, sum, min and max, and derives the running average.
 */
public class IntSummaryStatistics {

    private long count;
    private long sum;
    private int min;
    private int max;

    public IntSummaryStatistics() {
        this.count = 0L;
        this.sum = 0L;
        this.min = Integer.MAX_VALUE;
        this.max = Integer.MIN_VALUE;
    }

    public void accept(int value) {
        count++;
        sum += value;
        if (value < min) {
            min = value;
        }
        if (value > max) {
            max = value;
        }
    }

    public final long getCount() {
        return count;
    }

    public final long getSum() {
        return sum;
    }

    public final int getMin() {
        return min;
    }

    public final int getMax() {
        return max;
    }

    public final double getAverage() {
        return count == 0L ? 0.0d : (double) sum / (double) count;
    }

    public String toString() {
        return "IntSummaryStatistics{count=" + count
                + ", sum=" + sum
                + ", min=" + min
                + ", average=" + getAverage()
                + ", max=" + max
                + "}";
    }
}
