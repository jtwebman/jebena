import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// Access-log summarizer: end-to-end exercise of the fan-out #9 surface —
//   String.lines() to split the embedded log, LinkedHashMap access-order + removeEldestEntry
//   as a bounded LRU of recently-hit paths, Collections.sort with a chained Comparator,
//   TreeMap for ordered status tally, and Formatter grouping (%,d and %,.2f).
// stdout must be byte-identical to real java.
public class AccessLog {

    // A size-bounded LRU of path -> hit count, most-recently-accessed last.
    static final class Lru extends LinkedHashMap<String, Integer> {
        private final int cap;
        private final List<String> evicted = new ArrayList<>();

        Lru(int cap) {
            super(16, 0.75f, true); // access-order
            this.cap = cap;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
            if (size() > cap) {
                evicted.add(eldest.getKey());
                return true;
            }
            return false;
        }
    }

    static final String LOG =
        "GET /index 200 1024\n" +
        "GET /about 200 512\n" +
        "POST /login 200 256\n" +
        "GET /index 200 1024\n" +
        "GET /images/logo 200 8192\n" +
        "GET /about 304 0\n" +
        "POST /login 401 128\n" +
        "GET /index 200 1024\n" +
        "GET /cart 200 2048\n" +
        "GET /images/logo 200 8192\n" +
        "GET /checkout 500 640\n" +
        "GET /index 200 1024\n" +
        "POST /login 200 256\n" +
        "GET /about 200 512\n" +
        "GET /cart 200 2048\r\n" +
        "GET /index 200 1024";

    public static void main(String[] args) {
        Map<String, Integer> hits = new HashMap<>();
        Map<String, Long> bytesPerPath = new HashMap<>();
        TreeMap<Integer, Integer> statusTally = new TreeMap<>();
        Lru lru = new Lru(3);

        long totalBytes = 0;
        int totalReqs = 0;

        for (String line : LOG.lines().toArray(String[]::new)) {
            if (line.isBlank()) continue;
            String[] f = line.split(" ");
            String path = f[1];
            int status = Integer.parseInt(f[2]);
            long bytes = Long.parseLong(f[3]);

            totalReqs++;
            totalBytes += bytes;

            hits.merge(path, 1, Integer::sum);
            bytesPerPath.merge(path, bytes, Long::sum);
            statusTally.merge(status, 1, Integer::sum);

            // touch the LRU (access-order moves it to the tail; may evict the eldest)
            lru.merge(path, 1, Integer::sum);
        }

        double avg = totalReqs == 0 ? 0.0 : (double) totalBytes / totalReqs;

        System.out.println("== Access Log Summary ==");
        System.out.println(String.format("Requests:       %,d", totalReqs));
        System.out.println(String.format("Distinct paths: %,d", hits.size()));
        System.out.println(String.format("Total bytes:    %,d", totalBytes));
        System.out.println(String.format("Avg bytes/req:  %,.2f", avg));

        System.out.println();
        System.out.println("-- Paths (by hits desc, then name) --");
        List<String> paths = new ArrayList<>(hits.keySet());
        Collections.sort(paths, Comparator
            .comparingInt((String p) -> hits.get(p)).reversed()
            .thenComparing(Comparator.naturalOrder()));
        for (String p : paths) {
            System.out.println(String.format("%-16s %,5d hits  %,10d bytes",
                p, hits.get(p), bytesPerPath.get(p)));
        }

        System.out.println();
        System.out.println("-- Status codes --");
        for (Map.Entry<Integer, Integer> e : statusTally.entrySet()) {
            System.out.println(String.format("%d: %,d", e.getKey(), e.getValue()));
        }

        System.out.println();
        System.out.println("-- LRU (capacity 3, MRU last) --");
        System.out.println("evicted: " + lru.evicted);
        List<String> keys = new ArrayList<>(lru.keySet());
        System.out.println("resident: " + keys);
    }
}
