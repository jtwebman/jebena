/**
 * Run-length encode/decode round-trip. encode("aaabbc") -> "a3b2c1"; decode reverses
 * it (multi-digit counts supported). Exercises StringBuilder, Character.isDigit (on
 * the eager jbase path), char arithmetic, and a self-checking round-trip. Deterministic.
 */
public class RunLength {
    static String encode(String s) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            int j = i;
            while (j < n && s.charAt(j) == c) {
                j++;
            }
            sb.append(c).append(j - i);
            i = j;
        }
        return sb.toString();
    }

    static String decode(String s) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i++);
            int cnt = 0;
            while (i < n && Character.isDigit(s.charAt(i))) {
                cnt = cnt * 10 + (s.charAt(i) - '0');
                i++;
            }
            for (int k = 0; k < cnt; k++) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        String[] inputs = {
            "aaabbc",
            "wwwwwwwwwwww",
            "abcdef",
            "",
            "a",
            "mississippi",
            "xxxxxxxxxxxxxxxxxxxxxx",
        };
        for (String in : inputs) {
            String enc = encode(in);
            String dec = decode(enc);
            String ok = in.equals(dec) ? "OK" : "MISMATCH";
            System.out.println("'" + in + "' -> '" + enc + "' -> '" + dec + "' " + ok);
        }
    }
}
