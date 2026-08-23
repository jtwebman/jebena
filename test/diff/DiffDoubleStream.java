import java.util.stream.DoubleStream;
import java.util.stream.Stream;
import java.util.OptionalDouble;

public class DiffDoubleStream {

    public static int sumBasic() {
        double s = DoubleStream.of(1.5, 2.5, 3.0).sum();
        return (int) (s * 1000000);
    }

    public static int mapSum() {
        double s = DoubleStream.of(1.5, 2.5, 3.0).map(x -> x * 2).sum();
        return (int) (s * 1000000);
    }

    public static int filterCount() {
        long c = DoubleStream.of(1.0, 2.0, 3.0, 4.0).filter(x -> x > 2).count();
        return (int) c;
    }

    public static int averageBasic() {
        OptionalDouble a = DoubleStream.of(1.0, 2.0, 3.0, 4.0).average();
        return (int) (a.getAsDouble() * 1000000);
    }

    public static int averageEmpty() {
        OptionalDouble a = DoubleStream.of().average();
        return a.isPresent() ? 1 : 0;
    }

    public static int reduceProduct() {
        double r = DoubleStream.of(1.5, 2.0, 3.0).reduce(1.0, (a, b) -> a * b);
        return (int) (r * 1000000);
    }

    public static int minVal() {
        double m = DoubleStream.of(3.0, -1.5, 2.0, -4.25).min().getAsDouble();
        return (int) (m * 1000000);
    }

    public static int maxVal() {
        double m = DoubleStream.of(3.0, -1.5, 2.0, -4.25).max().getAsDouble();
        return (int) (m * 1000000);
    }

    public static int minEmpty() {
        return DoubleStream.of().min().isPresent() ? 1 : 0;
    }

    public static int boxedToArray() {
        Stream s = DoubleStream.of(1.25, 2.5, 3.75).boxed();
        double[] arr = DoubleStream.of(1.25, 2.5, 3.75).toArray();
        double acc = 0.0;
        for (int i = 0; i < arr.length; i++) {
            acc = acc * 31 + arr[i];
        }
        long boxedCount = s.count();
        return (int) (acc * 1000000) + (int) boxedCount;
    }
}
