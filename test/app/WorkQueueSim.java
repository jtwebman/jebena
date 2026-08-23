import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Deterministic single-threaded work-stealing scheduler simulation. Exercises the fan-out #10
// surface end-to-end: java.util.concurrent.ConcurrentLinkedDeque used as a double-ended ready
// queue (offerFirst/offerLast + pollFirst/pollLast steal), Collectors.groupingBy + summingInt
// for per-category totals, Map.merge(k, v, Integer::sum) (method-ref boxing path),
// String.codePoints()/lines(), java.util.Base64, and Formatter %,d grouping. stdout must be
// byte-identical to real java.
public class WorkQueueSim {

    static final class Task {
        final String name;
        final String cat;
        final int cost;
        Task(String name, String cat, int cost) {
            this.name = name;
            this.cat = cat;
            this.cost = cost;
        }
    }

    // name category cost — "U" category tasks are urgent (pushed to the front).
    static final String JOBS =
        "compile A 30\n" +
        "lint B 10\n" +
        "hotfix U 5\n" +
        "test A 45\n" +
        "package B 20\n" +
        "deploy U 8\n" +
        "docs C 12\n" +
        "bench A 60\n" +
        "audit C 25\n" +
        "notify B 7\n" +
        "rollback U 15\n" +
        "archive C 18";

    public static void main(String[] args) {
        List<Task> tasks = new ArrayList<>();
        for (String line : JOBS.lines().toArray(String[]::new)) {
            if (line.isBlank()) {
                continue;
            }
            String[] f = line.split(" ");
            tasks.add(new Task(f[0], f[1], Integer.parseInt(f[2])));
        }

        // Enqueue: urgent ("U") to the front, everything else to the back.
        ConcurrentLinkedDeque ready = new ConcurrentLinkedDeque();
        for (Task t : tasks) {
            if (t.cat.equals("U")) {
                ready.offerFirst(t);
            } else {
                ready.offerLast(t);
            }
        }

        // Process: normally take from the front; every 3rd step "steal" from the back instead.
        List<Task> order = new ArrayList<>();
        Map<String, Integer> catCost = new HashMap<>();
        int step = 0;
        long nameCodePoints = 0;
        while (!ready.isEmpty()) {
            step++;
            Task t = (Task) (step % 3 == 0 ? ready.pollLast() : ready.pollFirst());
            if (t == null) {
                break;
            }
            order.add(t);
            catCost.merge(t.cat, t.cost, Integer::sum);
            nameCodePoints += t.name.codePoints().sum();
        }

        // Per-category totals via the stream collector, cross-checked against the merge map.
        Map<String, Integer> grouped = (Map<String, Integer>) Stream.of(order.toArray())
            .collect(Collectors.groupingBy(t -> ((Task) t).cat,
                Collectors.summingInt(t -> ((Task) t).cost)));

        int totalCost = 0;
        for (Task t : order) {
            totalCost += t.cost;
        }

        System.out.println("== Work Queue Simulation ==");
        System.out.println(String.format("Tasks run:   %,d", order.size()));
        System.out.println(String.format("Total cost:  %,d", totalCost));
        System.out.println("Name codepoints sum: " + nameCodePoints);

        System.out.println();
        System.out.println("-- Execution order --");
        StringBuilder ob = new StringBuilder();
        for (int i = 0; i < order.size(); i++) {
            if (i > 0) {
                ob.append(',');
            }
            ob.append(order.get(i).name);
        }
        String orderStr = ob.toString();
        System.out.println(orderStr);
        System.out.println("order b64: " + Base64.getEncoder().encodeToString(orderStr.getBytes()));

        System.out.println();
        System.out.println("-- Per-category cost (sorted) --");
        List<String> cats = new ArrayList<>(catCost.keySet());
        Collections.sort(cats);
        for (String c : cats) {
            int viaMerge = catCost.get(c);
            int viaStream = grouped.get(c);
            String flag = viaMerge == viaStream ? "ok" : "MISMATCH";
            System.out.println(String.format("%-2s %,5d  (%s)", c, viaMerge, flag));
        }
    }
}
