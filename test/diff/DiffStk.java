public class DiffStk {

    public static int pushPop() {
        java.util.Stack s = new java.util.Stack();
        for (int i = 1; i <= 6; i++) s.push(new Integer(i));
        int acc = 0;
        while (!s.empty()) {
            Integer v = (Integer) s.pop();
            acc = acc * 31 + v.intValue();
        }
        return acc;
    }

    public static int peekTop() {
        java.util.Stack s = new java.util.Stack();
        s.push(new Integer(10));
        s.push(new Integer(20));
        s.push(new Integer(30));
        Integer a = (Integer) s.peek();
        Integer b = (Integer) s.peek();
        s.pop();
        Integer c = (Integer) s.peek();
        return a.intValue() * 100 + b.intValue() + c.intValue();
    }

    public static int emptyFlag() {
        java.util.Stack s = new java.util.Stack();
        int acc = 0;
        acc = acc * 31 + (s.empty() ? 1 : 0);
        s.push(new Integer(7));
        acc = acc * 31 + (s.empty() ? 1 : 0);
        s.pop();
        acc = acc * 31 + (s.empty() ? 1 : 0);
        acc = acc * 31 + (s.isEmpty() ? 1 : 0);
        return acc;
    }

    public static int searchDist() {
        java.util.Stack s = new java.util.Stack();
        s.push(new Integer(1));
        s.push(new Integer(2));
        s.push(new Integer(3));
        s.push(new Integer(4));
        int acc = 0;
        acc = acc * 31 + s.search(new Integer(4));
        acc = acc * 31 + s.search(new Integer(1));
        acc = acc * 31 + s.search(new Integer(3));
        acc = acc * 31 + s.search(new Integer(99));
        return acc;
    }

    public static int sizeTrack() {
        java.util.Stack s = new java.util.Stack();
        int acc = 0;
        acc = acc * 31 + s.size();
        s.push(new Integer(1));
        s.push(new Integer(2));
        acc = acc * 31 + s.size();
        s.push(new Integer(3));
        acc = acc * 31 + s.size();
        s.pop();
        s.pop();
        acc = acc * 31 + s.size();
        return acc;
    }

    public static int popAll() {
        java.util.Stack s = new java.util.Stack();
        for (int i = 0; i < 4; i++) s.push(new Integer(i * i));
        int acc = 0;
        int n = s.size();
        for (int i = 0; i < n; i++) {
            Integer v = (Integer) s.pop();
            acc = acc * 31 + v.intValue();
        }
        acc = acc * 31 + (s.isEmpty() ? 1 : 0);
        return acc;
    }

    public static int searchDup() {
        java.util.Stack s = new java.util.Stack();
        s.push(new Integer(5));
        s.push(new Integer(5));
        s.push(new Integer(9));
        s.push(new Integer(5));
        int acc = 0;
        acc = acc * 31 + s.search(new Integer(5));
        acc = acc * 31 + s.search(new Integer(9));
        return acc;
    }

    public static int singleton() {
        java.util.Stack s = new java.util.Stack();
        s.push(new Integer(42));
        int acc = 0;
        acc = acc * 31 + s.size();
        acc = acc * 31 + ((Integer) s.peek()).intValue();
        acc = acc * 31 + ((Integer) s.pop()).intValue();
        acc = acc * 31 + (s.empty() ? 1 : 0);
        return acc;
    }
}
