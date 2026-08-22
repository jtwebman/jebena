package java.util.stream;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.function.Function;

/**
 * Clean-room factories for {@link Collector}. Each returned collector consumes
 * the stream's realized element list and produces the final result.
 */
public class Collectors {

    private Collectors() {
    }

    public static Collector toList() {
        return new Collector() {
            public Object collect(ArrayList data) {
                ArrayList out = new ArrayList();
                for (int i = 0; i < data.size(); i++) {
                    out.add(data.get(i));
                }
                return out;
            }
        };
    }

    public static Collector toSet() {
        return new Collector() {
            public Object collect(ArrayList data) {
                HashSet out = new HashSet();
                for (int i = 0; i < data.size(); i++) {
                    out.add(data.get(i));
                }
                return out;
            }
        };
    }

    public static Collector joining() {
        return joining("", "", "");
    }

    public static Collector joining(CharSequence delimiter) {
        return joining(delimiter, "", "");
    }

    public static Collector joining(final CharSequence delimiter,
                                    final CharSequence prefix,
                                    final CharSequence suffix) {
        return new Collector() {
            public Object collect(ArrayList data) {
                StringBuilder sb = new StringBuilder();
                sb.append(prefix);
                for (int i = 0; i < data.size(); i++) {
                    if (i > 0) {
                        sb.append(delimiter);
                    }
                    sb.append(String.valueOf(data.get(i)));
                }
                sb.append(suffix);
                return sb.toString();
            }
        };
    }

    public static Collector counting() {
        return new Collector() {
            public Object collect(ArrayList data) {
                return Long.valueOf(data.size());
            }
        };
    }

    public static Collector groupingBy(final Function classifier) {
        return new Collector() {
            public Object collect(ArrayList data) {
                LinkedHashMap map = new LinkedHashMap();
                for (int i = 0; i < data.size(); i++) {
                    Object element = data.get(i);
                    Object key = classifier.apply(element);
                    Object bucket = map.get(key);
                    if (bucket == null) {
                        bucket = new ArrayList();
                        map.put(key, bucket);
                    }
                    ((ArrayList) bucket).add(element);
                }
                return map;
            }
        };
    }
}
