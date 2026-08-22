import java.util.ArrayDeque;

/**
 * A Reverse-Polish-Notation integer evaluator using ArrayDeque as a stack. Tokens
 * are space-separated; operators + - * /. Exercises ArrayDeque push/pop, String
 * .split, Integer.parseInt, integer arithmetic, and caught exceptions (empty stack
 * -> a thrown IllegalStateException; divide-by-zero -> ArithmeticException).
 */
public class Rpn {
    static int eval(String expr) {
        ArrayDeque stack = new ArrayDeque();
        String[] toks = expr.split("\\s+");
        for (String t : toks) {
            if (t.isEmpty()) {
                continue;
            }
            if (t.equals("+") || t.equals("-") || t.equals("*") || t.equals("/")) {
                if (stack.size() < 2) {
                    throw new IllegalStateException("stack underflow");
                }
                int b = ((Integer) stack.pop()).intValue();
                int a = ((Integer) stack.pop()).intValue();
                int r;
                if (t.equals("+")) {
                    r = a + b;
                } else if (t.equals("-")) {
                    r = a - b;
                } else if (t.equals("*")) {
                    r = a * b;
                } else {
                    r = a / b;
                }
                stack.push(Integer.valueOf(r));
            } else {
                stack.push(Integer.valueOf(Integer.parseInt(t)));
            }
        }
        if (stack.size() != 1) {
            throw new IllegalStateException("leftover operands: " + stack.size());
        }
        return ((Integer) stack.pop()).intValue();
    }

    public static void main(String[] args) {
        String[] exprs = {
            "3 4 +",
            "5 1 2 + 4 * + 3 -",
            "2 3 4 * +",
            "10 2 /",
            "1 0 /",     // divide by zero
            "1 +",       // underflow
            "1 2 3",     // leftover
        };
        for (String e : exprs) {
            try {
                System.out.println(e + " => " + eval(e));
            } catch (ArithmeticException ex) {
                System.out.println(e + " => arith: " + ex.getMessage());
            } catch (IllegalStateException ex) {
                System.out.println(e + " => state: " + ex.getMessage());
            }
        }
    }
}
