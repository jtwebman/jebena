import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Directed-graph BFS + Kahn topological sort. Adjacency is a TreeMap (sorted keys)
 * of ArrayList neighbor lists, so iteration order is deterministic and byte-comparable.
 * BFS from a source uses an ArrayDeque queue + a TreeSet visited; topo sort uses an
 * in-degree TreeMap + a sorted ready-queue and reports a cycle when not all nodes
 * are emitted. Exercises HashMap/TreeMap, ArrayDeque, TreeSet, boxing, and loops.
 */
public class Graph {
    static void edge(TreeMap adj, int u, int v) {
        Object lu = adj.get(Integer.valueOf(u));
        if (lu == null) {
            lu = new ArrayList();
            adj.put(Integer.valueOf(u), lu);
        }
        ((ArrayList) lu).add(Integer.valueOf(v));
        if (adj.get(Integer.valueOf(v)) == null) {
            adj.put(Integer.valueOf(v), new ArrayList());
        }
    }

    static String bfs(TreeMap adj, int src) {
        ArrayDeque q = new ArrayDeque();
        TreeSet seen = new TreeSet();
        StringBuilder sb = new StringBuilder();
        q.addLast(Integer.valueOf(src));
        seen.add(Integer.valueOf(src));
        while (!q.isEmpty()) {
            Integer cur = (Integer) q.pollFirst();
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(cur.toString());
            List ns = (List) adj.get(cur);
            for (int i = 0; i < ns.size(); i++) {
                Integer w = (Integer) ns.get(i);
                if (!seen.contains(w)) {
                    seen.add(w);
                    q.addLast(w);
                }
            }
        }
        return sb.toString();
    }

    static String topo(TreeMap adj) {
        TreeMap indeg = new TreeMap();
        for (Object k : adj.keySet()) {
            if (indeg.get(k) == null) {
                indeg.put(k, Integer.valueOf(0));
            }
        }
        for (Object e : adj.entrySet()) {
            Map.Entry en = (Map.Entry) e;
            List ns = (List) en.getValue();
            for (int i = 0; i < ns.size(); i++) {
                Integer w = (Integer) ns.get(i);
                indeg.put(w, Integer.valueOf(((Integer) indeg.get(w)).intValue() + 1));
            }
        }
        ArrayDeque ready = new ArrayDeque();
        for (Object e : indeg.entrySet()) {
            Map.Entry en = (Map.Entry) e;
            if (((Integer) en.getValue()).intValue() == 0) {
                ready.addLast(en.getKey());
            }
        }
        StringBuilder sb = new StringBuilder();
        int emitted = 0;
        while (!ready.isEmpty()) {
            Integer cur = (Integer) ready.pollFirst();
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(cur.toString());
            emitted++;
            List ns = (List) adj.get(cur);
            // collect newly-zeroed in sorted order for determinism
            TreeSet newlyZero = new TreeSet();
            for (int i = 0; i < ns.size(); i++) {
                Integer w = (Integer) ns.get(i);
                int d = ((Integer) indeg.get(w)).intValue() - 1;
                indeg.put(w, Integer.valueOf(d));
                if (d == 0) {
                    newlyZero.add(w);
                }
            }
            for (Object z : newlyZero) {
                ready.addLast(z);
            }
        }
        if (emitted != indeg.size()) {
            return "cycle";
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        TreeMap dag = new TreeMap();
        edge(dag, 1, 2);
        edge(dag, 1, 3);
        edge(dag, 2, 4);
        edge(dag, 3, 4);
        edge(dag, 4, 5);
        edge(dag, 3, 6);
        edge(dag, 6, 5);
        System.out.println("nodes: " + dag.size());
        System.out.println("bfs(1): " + bfs(dag, 1));
        System.out.println("topo:   " + topo(dag));

        TreeMap cyc = new TreeMap();
        edge(cyc, 1, 2);
        edge(cyc, 2, 3);
        edge(cyc, 3, 1); // cycle
        System.out.println("bfs(1) cyc: " + bfs(cyc, 1));
        System.out.println("topo cyc:   " + topo(cyc));
    }
}
