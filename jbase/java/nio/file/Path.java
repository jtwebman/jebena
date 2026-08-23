package java.nio.file;

import java.util.ArrayList;

public final class Path {
    private final String path;

    Path(String path) {
        this.path = path;
    }

    public boolean isAbsolute() {
        return path.startsWith("/");
    }

    public Path getRoot() {
        return isAbsolute() ? new Path("/") : null;
    }

    public String toString() {
        return path;
    }

    private ArrayList names() {
        ArrayList list = new ArrayList();
        int i = 0;
        int n = path.length();
        while (i < n) {
            while (i < n && path.charAt(i) == '/') {
                i++;
            }
            int start = i;
            while (i < n && path.charAt(i) != '/') {
                i++;
            }
            if (i > start) {
                list.add(path.substring(start, i));
            }
        }
        return list;
    }

    public int getNameCount() {
        return names().size();
    }

    public Path getName(int i) {
        ArrayList list = names();
        if (i < 0 || i >= list.size()) {
            throw new IllegalArgumentException();
        }
        return new Path((String) list.get(i));
    }

    public Path getFileName() {
        ArrayList list = names();
        if (list.isEmpty()) {
            return null;
        }
        return new Path((String) list.get(list.size() - 1));
    }

    public Path getParent() {
        int idx = path.lastIndexOf('/');
        if (idx < 0) {
            return null;
        }
        if (idx == 0) {
            // parent is root only if there is something after it
            if (path.length() > 1) {
                return new Path("/");
            }
            return null;
        }
        String parent = path.substring(0, idx);
        // strip trailing slashes from parent
        int end = parent.length();
        while (end > 1 && parent.charAt(end - 1) == '/') {
            end--;
        }
        parent = parent.substring(0, end);
        if (parent.isEmpty()) {
            return null;
        }
        return new Path(parent);
    }

    public Path resolve(String other) {
        if (other.startsWith("/")) {
            return new Path(other);
        }
        if (other.isEmpty()) {
            return this;
        }
        if (path.isEmpty()) {
            return new Path(other);
        }
        if (path.endsWith("/")) {
            return new Path(path + other);
        }
        return new Path(path + "/" + other);
    }

    public Path normalize() {
        boolean absolute = isAbsolute();
        ArrayList parts = names();
        ArrayList out = new ArrayList();
        for (int i = 0; i < parts.size(); i++) {
            String part = (String) parts.get(i);
            if (part.equals(".")) {
                continue;
            }
            if (part.equals("..")) {
                if (out.size() > 0 && !out.get(out.size() - 1).equals("..")) {
                    out.remove(out.size() - 1);
                } else if (!absolute) {
                    out.add("..");
                }
                // for absolute, ".." at root is dropped
                continue;
            }
            out.add(part);
        }
        StringBuilder sb = new StringBuilder();
        if (absolute) {
            sb.append("/");
        }
        for (int i = 0; i < out.size(); i++) {
            if (i > 0) {
                sb.append("/");
            }
            sb.append((String) out.get(i));
        }
        String result = sb.toString();
        if (result.isEmpty()) {
            result = absolute ? "/" : "";
        }
        return new Path(result);
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Path)) {
            return false;
        }
        return path.equals(((Path) o).path);
    }

    public int hashCode() {
        return path.hashCode();
    }
}
