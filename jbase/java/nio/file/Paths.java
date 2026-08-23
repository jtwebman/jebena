package java.nio.file;

public final class Paths {
    private Paths() {}

    public static Path get(String first, String... more) {
        StringBuilder sb = new StringBuilder();
        sb.append(first);
        if (more != null) {
            for (int i = 0; i < more.length; i++) {
                String part = more[i];
                if (part == null || part.isEmpty()) {
                    continue;
                }
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '/') {
                    sb.append('/');
                }
                sb.append(part);
            }
        }
        return new Path(sb.toString());
    }
}
