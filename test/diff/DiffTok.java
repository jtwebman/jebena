public class DiffTok {

    public static int countWs() {
        java.util.StringTokenizer t = new java.util.StringTokenizer("the quick brown fox");
        return t.countTokens();
    }

    public static int countComma() {
        // "a,b,,c,d" with delim "," -> empty tokens skipped: a,b,c,d = 4 tokens
        java.util.StringTokenizer t = new java.util.StringTokenizer("a,b,,c,d", ",");
        int acc = 0;
        while (t.hasMoreTokens()) {
            String tok = t.nextToken();
            acc = acc * 31 + tok.length();
        }
        return acc;
    }

    public static int loopCount() {
        java.util.StringTokenizer t = new java.util.StringTokenizer("  lots   of   space  ");
        int n = 0;
        while (t.hasMoreTokens()) {
            t.nextToken();
            n++;
        }
        return n;
    }

    public static int mixedDelim() {
        java.util.StringTokenizer t = new java.util.StringTokenizer(" a;b , c ", " ;,");
        int acc = 0;
        while (t.hasMoreTokens()) {
            String tok = t.nextToken();
            for (int i = 0; i < tok.length(); i++) {
                acc = acc * 31 + tok.charAt(i);
            }
        }
        return acc;
    }

    public static int singleTok() {
        java.util.StringTokenizer t = new java.util.StringTokenizer("solitary");
        int acc = t.countTokens();
        String tok = t.nextToken();
        acc = acc * 31 + tok.length();
        return acc;
    }

    public static int emptyInput() {
        java.util.StringTokenizer t = new java.util.StringTokenizer("");
        int acc = t.countTokens();
        acc = acc * 2 + (t.hasMoreTokens() ? 1 : 0);
        return acc;
    }

    public static int changeDelim() {
        // nextToken(String) changes the delimiter set mid-stream
        java.util.StringTokenizer t = new java.util.StringTokenizer("a,b;c d");
        int acc = 0;
        String first = t.nextToken(",");   // "a"
        acc = acc * 31 + first.length();
        String second = t.nextToken(";");  // ",b" (leading comma now not a delim)
        acc = acc * 31 + second.length();
        while (t.hasMoreTokens()) {
            acc = acc * 31 + t.nextToken().length();
        }
        return acc;
    }

    public static int elementsApi() {
        java.util.StringTokenizer t = new java.util.StringTokenizer("one two three");
        int acc = 0;
        while (t.hasMoreElements()) {
            String tok = (String) t.nextElement();
            acc = acc * 31 + tok.length();
        }
        return acc;
    }
}
