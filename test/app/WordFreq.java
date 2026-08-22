import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A small end-to-end program: tokenize text, count word frequencies in a HashMap,
 * sort entries (count desc, then word asc), print a report, run a stream reduction,
 * and recover from a caught exception. Uses only APIs jebena's jbase implements.
 */
public class WordFreq {
    // recursion: triangular number, just to exercise a recursive call in the trace-able stack
    static int triangular(int n) {
        return n <= 0 ? 0 : n + triangular(n - 1);
    }

    public static void main(String[] args) {
        String text = "the quick brown fox the lazy dog the fox jumps over the lazy dog quick fox";
        if (args.length > 0) {
            text = String.join(" ", args);
        }

        String[] words = text.split("\\s+");
        Map<String, Integer> counts = new HashMap<>();
        for (String w : words) {
            if (w.isEmpty()) {
                continue;
            }
            Integer cur = counts.get(w);
            int n = (cur == null) ? 0 : cur.intValue();
            counts.put(w, Integer.valueOf(n + 1));
        }

        List<Map.Entry<String, Integer>> entries = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            entries.add(e);
        }
        Collections.sort(entries, (a, b) -> {
            int c = b.getValue().intValue() - a.getValue().intValue();
            return c != 0 ? c : a.getKey().compareTo(b.getKey());
        });

        System.out.println("total words: " + words.length);
        System.out.println("distinct: " + counts.size());
        for (Map.Entry<String, Integer> e : entries) {
            System.out.println(e.getKey() + " = " + e.getValue());
        }

        // stream reduction over the counts
        int sum = counts.values().stream().mapToInt(v -> ((Integer) v).intValue()).sum();
        System.out.println("sum of counts: " + sum);

        // stream: the alphabetically-first word among those tied for the max count
        int max = 0;
        for (Map.Entry<String, Integer> e : entries) {
            if (e.getValue().intValue() > max) {
                max = e.getValue().intValue();
            }
        }
        final int fmax = max;
        Object top = entries.stream()
                .filter(x -> ((Map.Entry) x).getValue().equals(Integer.valueOf(fmax)))
                .map(x -> ((Map.Entry) x).getKey())
                .sorted()
                .findFirst()
                .orElse("none");
        System.out.println("top word: " + top + " (" + max + ")");

        System.out.println("triangular(10): " + triangular(10));

        // caught exception path
        try {
            Integer.parseInt("not-a-number");
            System.out.println("parsed?!");
        } catch (NumberFormatException ex) {
            System.out.println("caught NumberFormatException");
        }
    }
}
