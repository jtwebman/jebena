/**
 * A tiny recursive-descent integer calculator: tokenizes on the fly and evaluates
 * expr -> term (('+'|'-') term)*, term -> factor (('*'|'/') factor)*, factor ->
 * number | '(' expr ')' | '-' factor. Exercises recursion, char/String ops,
 * Integer.parseInt, integer arithmetic, and a caught ArithmeticException (/ by zero).
 */
public class Calc {
    static String s;
    static int pos;

    static void skip() {
        while (pos < s.length() && s.charAt(pos) == ' ') {
            pos++;
        }
    }

    static int number() {
        skip();
        int start = pos;
        while (pos < s.length() && s.charAt(pos) >= '0' && s.charAt(pos) <= '9') {
            pos++;
        }
        return Integer.parseInt(s.substring(start, pos));
    }

    static int factor() {
        skip();
        char c = s.charAt(pos);
        if (c == '(') {
            pos++;
            int v = expr();
            skip();
            pos++; // consume ')'
            return v;
        }
        if (c == '-') {
            pos++;
            return -factor();
        }
        return number();
    }

    static int term() {
        int v = factor();
        while (true) {
            skip();
            if (pos >= s.length()) {
                break;
            }
            char c = s.charAt(pos);
            if (c == '*') {
                pos++;
                v = v * factor();
            } else if (c == '/') {
                pos++;
                v = v / factor();
            } else {
                break;
            }
        }
        return v;
    }

    static int expr() {
        int v = term();
        while (true) {
            skip();
            if (pos >= s.length()) {
                break;
            }
            char c = s.charAt(pos);
            if (c == '+') {
                pos++;
                v = v + term();
            } else if (c == '-') {
                pos++;
                v = v - term();
            } else {
                break;
            }
        }
        return v;
    }

    static int eval(String in) {
        s = in;
        pos = 0;
        return expr();
    }

    public static void main(String[] args) {
        String[] exprs = {
            "1 + 2 * 3",
            "(1 + 2) * 3",
            "2 * (3 + 4) - 5",
            "100 / 7",
            "-(3 + 4) * 2",
            "2 + 3 / 0",
        };
        for (String e : exprs) {
            try {
                System.out.println(e + " = " + eval(e));
            } catch (ArithmeticException ex) {
                System.out.println(e + " -> error: " + ex.getMessage());
            }
        }
    }
}
