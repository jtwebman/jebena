import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Dijkstra single-source shortest paths on a small weighted digraph. Adjacency is a
 * TreeMap of ArrayList<int[]{to,weight}>; distances via a repeated linear min-scan
 * (no PriorityQueue). Exercises TreeMap, ArrayList holding int[] elements, arrays,
 * boxing, and a classic algorithm end-to-end. Deterministic, byte-comparable.
 */
public class Dijkstra {
    static final int INF = 1000000000;

    static void addEdge(TreeMap g, int u, int v, int w) {
        List lu = (List) g.get(Integer.valueOf(u));
        if (lu == null) {
            lu = new ArrayList();
            g.put(Integer.valueOf(u), lu);
        }
        lu.add(new int[] { v, w });
        if (g.get(Integer.valueOf(v)) == null) {
            g.put(Integer.valueOf(v), new ArrayList());
        }
    }

    public static void main(String[] args) {
        TreeMap g = new TreeMap();
        addEdge(g, 0, 1, 4);
        addEdge(g, 0, 2, 1);
        addEdge(g, 2, 1, 2);
        addEdge(g, 1, 3, 1);
        addEdge(g, 2, 3, 5);
        addEdge(g, 3, 4, 3);
        addEdge(g, 1, 4, 12);

        int n = g.size();
        int[] dist = new int[n];
        boolean[] done = new boolean[n];
        for (int i = 0; i < n; i++) {
            dist[i] = INF;
        }
        dist[0] = 0;
        for (int iter = 0; iter < n; iter++) {
            int u = -1;
            int best = INF;
            for (int i = 0; i < n; i++) {
                if (!done[i] && dist[i] < best) {
                    best = dist[i];
                    u = i;
                }
            }
            if (u < 0) {
                break;
            }
            done[u] = true;
            List edges = (List) g.get(Integer.valueOf(u));
            for (int k = 0; k < edges.size(); k++) {
                int[] e = (int[]) edges.get(k);
                int v = e[0];
                int w = e[1];
                if (dist[u] != INF && dist[u] + w < dist[v]) {
                    dist[v] = dist[u] + w;
                }
            }
        }
        for (int i = 0; i < n; i++) {
            String d = (dist[i] == INF) ? "INF" : String.valueOf(dist[i]);
            System.out.println("dist[" + i + "] = " + d);
        }
    }
}
