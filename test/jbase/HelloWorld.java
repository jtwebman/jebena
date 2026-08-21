package jebena;

// Prints to System.out; the output-smoke diffs stdout against real java.
public class HelloWorld {
    public static void run() {
        System.out.println("Hello, World!");
        System.out.println(42);
        System.out.print("no-newline-");
        System.out.println(7);
        System.out.println(3.5);
        System.out.println(true);
        System.out.println('Z');
        System.out.println(1000000000000L);
        for (int i = 0; i < 3; i++) {
            System.out.println("line " + i + " sq=" + (i * i));
        }
        StringBuilder sb = new StringBuilder();
        sb.append("built:").append(1).append(',').append(2.0).append(',').append(true);
        System.out.println(sb.toString());
        java.util.List<Integer> xs = new java.util.ArrayList<>();
        xs.add(3); xs.add(1); xs.add(2);
        java.util.Collections.sort(xs);
        System.out.println(xs.toString());
    }
}
