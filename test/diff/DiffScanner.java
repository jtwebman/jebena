/**
 * Differential coverage for java.util.Scanner over a String source: whitespace
 * tokenizing (hasNext/next), typed reads (hasNextInt/nextInt, hasNextLong/
 * nextLong, hasNextDouble/nextDouble), and line reads (hasNextLine/nextLine).
 * Each case folds results into a deterministic int checksum (acc = acc*31 + v).
 */
public class DiffScanner {
    private static int b(boolean v) {
        return v ? 1 : 0;
    }

    static int ints() {
        java.util.Scanner s = new java.util.Scanner("10 20 30");
        int acc = 0;
        while (s.hasNextInt()) {
            acc = acc * 31 + s.nextInt();
        }
        s.close();
        return acc;
    }

    static int mixed() {
        java.util.Scanner s = new java.util.Scanner("3.5 x 7");
        int acc = 0;
        acc = acc * 31 + b(s.hasNextInt());     // 0
        acc = acc * 31 + b(s.hasNextDouble());  // 1
        double d = s.nextDouble();              // 3.5
        acc = acc * 31 + (int) (d * 2);         // 7
        String w = s.next();                    // "x"
        acc = acc * 31 + w.charAt(0);           // 120
        acc = acc * 31 + b(s.hasNextInt());     // 1
        acc = acc * 31 + s.nextInt();           // 7
        s.close();
        return acc;
    }

    static int lines() {
        java.util.Scanner s = new java.util.Scanner("line1\nline2\n");
        int acc = 0;
        while (s.hasNextLine()) {
            String ln = s.nextLine();
            acc = acc * 31 + ln.length();
            for (int i = 0; i < ln.length(); i++) {
                acc = acc * 31 + ln.charAt(i);
            }
        }
        s.close();
        return acc;
    }

    static int emptyScanner() {
        java.util.Scanner s = new java.util.Scanner("");
        int acc = 0;
        acc = acc * 10 + b(s.hasNext());      // 0
        acc = acc * 10 + b(s.hasNextInt());   // 0
        acc = acc * 10 + b(s.hasNextLine());  // 0
        s.close();
        return acc;
    }

    static int longs() {
        java.util.Scanner s = new java.util.Scanner("100000 9999999999");
        int acc = 0;
        acc = acc * 31 + b(s.hasNextLong()); // 1
        long a = s.nextLong();               // 100000
        acc = acc * 31 + (int) a;
        acc = acc * 31 + b(s.hasNextLong()); // 1
        long c = s.nextLong();               // 9999999999
        acc = acc * 31 + (int) c;
        s.close();
        return acc;
    }

    static int doubles() {
        java.util.Scanner s = new java.util.Scanner("1.5 2.25 -3.0");
        int acc = 0;
        while (s.hasNextDouble()) {
            acc = acc * 31 + (int) (s.nextDouble() * 4);
        }
        s.close();
        return acc;
    }

    static int nextLineAfterToken() {
        java.util.Scanner s = new java.util.Scanner("42 hello world\nsecond line");
        int acc = 0;
        acc = acc * 31 + s.nextInt();        // 42, position now at the space
        String rest = s.nextLine();          // " hello world"
        acc = acc * 31 + rest.length();
        for (int i = 0; i < rest.length(); i++) {
            acc = acc * 31 + rest.charAt(i);
        }
        String l2 = s.nextLine();            // "second line"
        acc = acc * 31 + l2.length();
        s.close();
        return acc;
    }

    static int whitespaceRuns() {
        java.util.Scanner s = new java.util.Scanner("  a   bb  ccc  ");
        int acc = 0;
        while (s.hasNext()) {
            String t = s.next();
            acc = acc * 31 + t.length();
            for (int i = 0; i < t.length(); i++) {
                acc = acc * 31 + t.charAt(i);
            }
        }
        s.close();
        return acc;
    }

    static int negatives() {
        java.util.Scanner s = new java.util.Scanner("-5 -10 3");
        int acc = 0;
        while (s.hasNextInt()) {
            acc = acc * 31 + s.nextInt();
        }
        s.close();
        return acc;
    }

    static int tabsAndNewlines() {
        java.util.Scanner s = new java.util.Scanner("a\tb\nc\r\nd");
        int acc = 0;
        while (s.hasNext()) {
            String t = s.next();
            acc = acc * 31 + t.charAt(0);
        }
        s.close();
        return acc;
    }
}
