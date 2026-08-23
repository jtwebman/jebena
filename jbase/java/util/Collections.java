package java.util;

public final class Collections {
    private Collections() {}

    // Stable insertion sort by natural ordering (Comparable).
    public static void sort(List list) {
        int n = list.size();
        for (int i = 1; i < n; i++) {
            Object key = list.get(i);
            int j = i - 1;
            while (j >= 0 && ((Comparable) list.get(j)).compareTo(key) > 0) {
                list.set(j + 1, list.get(j));
                j--;
            }
            list.set(j + 1, key);
        }
    }

    public static void sort(List list, Comparator c) {
        int n = list.size();
        for (int i = 1; i < n; i++) {
            Object key = list.get(i);
            int j = i - 1;
            while (j >= 0 && c.compare(list.get(j), key) > 0) {
                list.set(j + 1, list.get(j));
                j--;
            }
            list.set(j + 1, key);
        }
    }

    public static void reverse(List list) {
        int i = 0;
        int j = list.size() - 1;
        while (i < j) {
            Object t = list.get(i);
            list.set(i, list.get(j));
            list.set(j, t);
            i++;
            j--;
        }
    }

    public static Object max(Collection coll) {
        Iterator it = coll.iterator();
        Object max = it.next();
        while (it.hasNext()) {
            Object next = it.next();
            if (((Comparable) next).compareTo(max) > 0) {
                max = next;
            }
        }
        return max;
    }

    public static Object min(Collection coll) {
        Iterator it = coll.iterator();
        Object min = it.next();
        while (it.hasNext()) {
            Object next = it.next();
            if (((Comparable) next).compareTo(min) < 0) {
                min = next;
            }
        }
        return min;
    }

    public static List singletonList(Object o) {
        ArrayList list = new ArrayList();
        list.add(o);
        return list;
    }

    public static List nCopies(int n, Object o) {
        if (n < 0) {
            throw new IllegalArgumentException("List length = " + n);
        }
        ArrayList list = new ArrayList();
        for (int i = 0; i < n; i++) {
            list.add(o);
        }
        return list;
    }

    public static int frequency(Collection c, Object o) {
        int count = 0;
        java.util.Iterator it = c.iterator();
        while (it.hasNext()) {
            Object e = it.next();
            if (o == null ? e == null : o.equals(e)) {
                count++;
            }
        }
        return count;
    }

    public static void swap(List list, int i, int j) {
        Object a = list.get(i);
        list.set(i, list.get(j));
        list.set(j, a);
    }

    public static List emptyList() {
        return new ArrayList(0);
    }
}
