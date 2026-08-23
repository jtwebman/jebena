import java.nio.file.Path;
import java.nio.file.Paths;

public class DiffPath {
    private static int csum(String s) {
        int acc = 0;
        for (int i = 0; i < s.length(); i++) {
            acc = acc * 31 + s.charAt(i);
        }
        return acc;
    }

    public static int fileName() {
        return csum(Paths.get("/a/b/c.txt").getFileName().toString());
    }

    public static int parent() {
        return csum(Paths.get("/a/b/c.txt").getParent().toString());
    }

    public static int nameCount() {
        return Paths.get("/a/b/c.txt").getNameCount();
    }

    public static int nameAt1() {
        return csum(Paths.get("/a/b/c.txt").getName(1).toString());
    }

    public static int resolveRel() {
        return csum(Paths.get("/a/b").resolve("d/e").toString());
    }

    public static int resolveAbs() {
        return csum(Paths.get("/a/b").resolve("/x/y").toString());
    }

    public static int normalizeDots() {
        return csum(Paths.get("/a/b/../c/./d").normalize().toString());
    }

    public static int normalizeRel() {
        return csum(Paths.get("a/./b/../../c").normalize().toString());
    }

    public static int joinMulti() {
        return csum(Paths.get("a", "b", "c").toString());
    }

    public static int absAbsolute() {
        return Paths.get("/x").isAbsolute() ? 1 : 0;
    }

    public static int absRelative() {
        return Paths.get("x").isAbsolute() ? 1 : 0;
    }

    public static int rootAbs() {
        Path r = Paths.get("/a/b").getRoot();
        return r == null ? -1 : csum(r.toString());
    }

    public static int rootRel() {
        return Paths.get("a/b").getRoot() == null ? 1 : 0;
    }
}
