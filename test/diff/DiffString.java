/**
 * Differential coverage for the newly-added clean-room java.lang.String methods:
 * contains, repeat, isBlank, strip / stripLeading / stripTrailing, and the static
 * join. Every method returns a deterministic int checked byte-for-byte vs real
 * java, exercising jbase String bytecode via EAGER classpath.
 */
public class DiffString {

    private static int b(boolean v) {
        return v ? 1 : 0;
    }

    static int contains() {
        String s = "hello world";
        return b(s.contains("o w")) * 1000
                + b(s.contains("world")) * 100
                + b(s.contains("xyz")) * 10
                + b(s.contains("")); // empty is always contained -> 1
    } // 1101

    static int repeat() {
        return "ab".repeat(3).length() * 100 // "ababab" -> 6
                + "x".repeat(0).length() * 10 // "" -> 0
                + "yz".repeat(1).length(); // "yz" -> 2
    } // 602

    static int repeatContent() {
        String r = "-=".repeat(4); // "-=-=-=-="
        int acc = 0;
        for (int i = 0; i < r.length(); i++) {
            acc += r.charAt(i);
        }
        return acc; // 4*('-'+'=') = 4*(45+61) = 424
    }

    static int isBlank() {
        return b("   \t\n".isBlank()) * 100
                + b("".isBlank()) * 10
                + b("  x  ".isBlank()); // 110
    }

    static int strip() {
        return "  hi there  ".strip().length() * 100 // "hi there" -> 8
                + "  lead".stripLeading().length() * 10 // "lead" -> wait, 4 -> use %? keep len
                + "trail  ".stripTrailing().length(); // "trail" -> 5
    } // 800 + 40 + 5 = 845

    static int stripEmpty() {
        return "     ".strip().length() * 100 // "" -> 0
                + "\t\t".stripLeading().length() * 10 // "" -> 0
                + "nochange".stripTrailing().length(); // 8
    } // 8

    static int join() {
        String j = String.join("-", "a", "b", "c"); // "a-b-c"
        return j.length() * 10 + b(j.equals("a-b-c")); // 5*10 + 1 = 51
    }

    static int joinEmpty() {
        return String.join(",").length() * 10 // no elements -> "" -> 0
                + String.join("::", "solo").length(); // "solo" -> 4
    } // 4

    static int splitComma() {
        String[] parts = "a,bb,ccc,d".split(",");
        int acc = 0;
        for (String p : parts) {
            acc = acc * 10 + p.length();
        }
        return acc * 10 + parts.length; // lengths 1,2,3,1 -> 1231, *10 + 4 = 12314
    }

    static int splitTrailingEmpty() {
        // limit 0 drops trailing empty strings: "a,b,,," -> ["a","b"]
        return "a,b,,,".split(",").length; // 2
    }

    static int splitLimit() {
        // limit 2 -> ["a", "b,c,d"]
        String[] parts = "a,b,c,d".split(",", 2);
        return parts.length * 100 + parts[1].length(); // 2*100 + len("b,c,d")=5 -> 205
    }

    static int splitRegex() {
        // split on runs of digits
        String[] parts = "foo12bar345baz".split("\\d+");
        int acc = 0;
        for (String p : parts) {
            acc += p.length();
        }
        return acc * 10 + parts.length; // "foo"(3)+"bar"(3)+"baz"(3)=9, *10 + 3 = 93
    }

    static int replaceSeq() {
        String r = "one fish two fish".replace("fish", "cat"); // "one cat two cat"
        return r.length(); // 15
    }

    static int replaceEmptyTarget() {
        // literal replace with empty target inserts between every char and at ends
        return "ab".replace("", "-").length(); // "-a-b-" -> 5
    }

    static int matches() {
        return b("12345".matches("\\d+")) * 100
                + b("12a45".matches("\\d+")) * 10
                + b("hello".matches("[a-z]+")); // 101
    }
}
