import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A JSON-ish pretty-printer over a nested LinkedHashMap/ArrayList model. Exercises
 * recursion, instanceof type dispatch, ordered-map iteration, StringBuilder, and
 * boxing (Integer/Boolean). Deterministic (insertion-ordered), so byte-comparable.
 */
public class JsonPrint {
    static void pad(StringBuilder sb, int n) {
        for (int i = 0; i < n; i++) {
            sb.append(' ');
        }
    }

    static void render(Object v, StringBuilder sb, int indent) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String) {
            sb.append('"').append((String) v).append('"');
        } else if (v instanceof Integer || v instanceof Boolean) {
            sb.append(v.toString());
        } else if (v instanceof Map) {
            Map m = (Map) v;
            if (m.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append("{\n");
            boolean first = true;
            for (Object e : m.entrySet()) {
                Map.Entry en = (Map.Entry) e;
                if (!first) {
                    sb.append(",\n");
                }
                first = false;
                pad(sb, indent + 2);
                sb.append('"').append(en.getKey().toString()).append("\": ");
                render(en.getValue(), sb, indent + 2);
            }
            sb.append('\n');
            pad(sb, indent);
            sb.append('}');
        } else if (v instanceof List) {
            List a = (List) v;
            if (a.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append("[\n");
            for (int i = 0; i < a.size(); i++) {
                if (i > 0) {
                    sb.append(",\n");
                }
                pad(sb, indent + 2);
                render(a.get(i), sb, indent + 2);
            }
            sb.append('\n');
            pad(sb, indent);
            sb.append(']');
        } else {
            sb.append(v.toString());
        }
    }

    public static void main(String[] args) {
        LinkedHashMap root = new LinkedHashMap();
        root.put("name", "jebena");
        root.put("version", Integer.valueOf(1));
        root.put("stable", Boolean.valueOf(true));
        root.put("nothing", null);
        ArrayList langs = new ArrayList();
        langs.add("java");
        langs.add("zig");
        root.put("langs", langs);
        LinkedHashMap runtime = new LinkedHashMap();
        runtime.put("gc", "mark-compact");
        runtime.put("threads", Integer.valueOf(4));
        runtime.put("tags", new ArrayList());
        root.put("runtime", runtime);
        StringBuilder sb = new StringBuilder();
        render(root, sb, 0);
        System.out.println(sb.toString());
    }
}
