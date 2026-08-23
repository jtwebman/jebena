public class DiffVec {
    public static int addGet() {
        java.util.Vector v = new java.util.Vector();
        v.add(Integer.valueOf(10));
        v.add(Integer.valueOf(20));
        v.add(Integer.valueOf(30));
        v.addElement(Integer.valueOf(40));
        v.addElement(Integer.valueOf(50));
        v.addElement(Integer.valueOf(60));
        int acc = 0;
        for (int i = 0; i < v.size(); i++) {
            Integer x = (Integer) v.get(i);
            acc = acc * 31 + x.intValue();
        }
        Integer y = (Integer) v.elementAt(3);
        acc = acc * 31 + y.intValue();
        return acc;
    }

    public static int setElem() {
        java.util.Vector v = new java.util.Vector();
        for (int i = 0; i < 6; i++) v.add(Integer.valueOf(i + 1));
        v.set(2, Integer.valueOf(99));
        v.set(0, Integer.valueOf(-5));
        int acc = 0;
        for (int i = 0; i < v.size(); i++) {
            Integer x = (Integer) v.get(i);
            acc = acc * 31 + x.intValue();
        }
        return acc;
    }

    public static int insertAt() {
        java.util.Vector v = new java.util.Vector();
        for (int i = 0; i < 4; i++) v.add(Integer.valueOf(i + 1));
        v.insertElementAt(Integer.valueOf(100), 0);
        v.insertElementAt(Integer.valueOf(200), 2);
        v.insertElementAt(Integer.valueOf(300), v.size());
        int acc = v.size();
        for (int i = 0; i < v.size(); i++) {
            Integer x = (Integer) v.get(i);
            acc = acc * 31 + x.intValue();
        }
        return acc;
    }

    public static int removeAt() {
        java.util.Vector v = new java.util.Vector();
        for (int i = 0; i < 6; i++) v.add(Integer.valueOf(i * 10));
        Integer r = (Integer) v.remove(0);
        v.removeElementAt(2);
        int acc = r.intValue();
        acc = acc * 31 + v.size();
        for (int i = 0; i < v.size(); i++) {
            Integer x = (Integer) v.get(i);
            acc = acc * 31 + x.intValue();
        }
        return acc;
    }

    public static int removeObj() {
        java.util.Vector v = new java.util.Vector();
        v.add(Integer.valueOf(5));
        v.add(Integer.valueOf(7));
        v.add(Integer.valueOf(5));
        v.add(Integer.valueOf(9));
        boolean b1 = v.remove(Integer.valueOf(5));
        boolean b2 = v.remove(Integer.valueOf(42));
        int acc = (b1 ? 1 : 0);
        acc = acc * 31 + (b2 ? 1 : 0);
        acc = acc * 31 + v.size();
        for (int i = 0; i < v.size(); i++) {
            Integer x = (Integer) v.get(i);
            acc = acc * 31 + x.intValue();
        }
        return acc;
    }

    public static int indexOfHitMiss() {
        java.util.Vector v = new java.util.Vector();
        v.add(Integer.valueOf(3));
        v.add(Integer.valueOf(6));
        v.add(Integer.valueOf(3));
        v.add(Integer.valueOf(9));
        int acc = v.indexOf(Integer.valueOf(3));
        acc = acc * 31 + v.indexOf(Integer.valueOf(9));
        acc = acc * 31 + v.indexOf(Integer.valueOf(77));
        acc = acc * 31 + (v.contains(Integer.valueOf(6)) ? 1 : 0);
        acc = acc * 31 + (v.contains(Integer.valueOf(100)) ? 1 : 0);
        return acc;
    }

    public static int firstLast() {
        java.util.Vector v = new java.util.Vector();
        v.add(Integer.valueOf(11));
        v.add(Integer.valueOf(22));
        v.add(Integer.valueOf(33));
        Integer f = (Integer) v.firstElement();
        Integer l = (Integer) v.lastElement();
        int acc = f.intValue();
        acc = acc * 31 + l.intValue();
        acc = acc * 31 + (v.isEmpty() ? 1 : 0);
        return acc;
    }

    public static int clearEmpty() {
        java.util.Vector v = new java.util.Vector();
        int acc = (v.isEmpty() ? 1 : 0);
        v.add(Integer.valueOf(1));
        v.add(Integer.valueOf(2));
        acc = acc * 31 + (v.isEmpty() ? 1 : 0);
        v.clear();
        acc = acc * 31 + (v.isEmpty() ? 1 : 0);
        acc = acc * 31 + v.size();
        return acc;
    }
}
