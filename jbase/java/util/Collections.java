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

    public static Set emptySet() {
        return new UnmodifiableSet(new HashSet());
    }

    public static Map emptyMap() {
        return new UnmodifiableMap(new HashMap());
    }

    public static Set singleton(Object o) {
        HashSet s = new HashSet();
        s.add(o);
        return new UnmodifiableSet(s);
    }

    public static Object max(Collection coll, Comparator comp) {
        Iterator it = coll.iterator();
        Object max = it.next();
        while (it.hasNext()) {
            Object next = it.next();
            if (comp.compare(next, max) > 0) {
                max = next;
            }
        }
        return max;
    }

    public static Object min(Collection coll, Comparator comp) {
        Iterator it = coll.iterator();
        Object min = it.next();
        while (it.hasNext()) {
            Object next = it.next();
            if (comp.compare(next, min) < 0) {
                min = next;
            }
        }
        return min;
    }

    public static int binarySearch(List list, Object key, Comparator c) {
        int low = 0;
        int high = list.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            Object midVal = list.get(mid);
            int cmp = c.compare(midVal, key);
            if (cmp < 0) {
                low = mid + 1;
            } else if (cmp > 0) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }

    public static boolean disjoint(Collection c1, Collection c2) {
        Iterator it = c1.iterator();
        while (it.hasNext()) {
            if (c2.contains(it.next())) {
                return false;
            }
        }
        return true;
    }

    public static boolean addAll(Collection c, Object... elements) {
        boolean result = false;
        for (int i = 0; i < elements.length; i++) {
            if (c.add(elements[i])) {
                result = true;
            }
        }
        return result;
    }

    public static void fill(List list, Object obj) {
        int size = list.size();
        for (int i = 0; i < size; i++) {
            list.set(i, obj);
        }
    }

    public static List unmodifiableList(List list) {
        return new UnmodifiableList(list);
    }

    public static Set unmodifiableSet(Set set) {
        return new UnmodifiableSet(set);
    }

    public static Map unmodifiableMap(Map m) {
        return new UnmodifiableMap(m);
    }

    private static class UnmodifiableCollection implements Collection {
        final Collection c;

        UnmodifiableCollection(Collection c) {
            this.c = c;
        }

        public int size() {
            return c.size();
        }

        public boolean isEmpty() {
            return c.isEmpty();
        }

        public boolean contains(Object o) {
            return c.contains(o);
        }

        public Iterator iterator() {
            final Iterator it = c.iterator();
            return new Iterator() {
                public boolean hasNext() {
                    return it.hasNext();
                }

                public Object next() {
                    return it.next();
                }

                public void remove() {
                    throw new java.lang.UnsupportedOperationException();
                }
            };
        }

        public boolean add(Object e) {
            throw new java.lang.UnsupportedOperationException();
        }

        public boolean remove(Object o) {
            throw new java.lang.UnsupportedOperationException();
        }

        public void clear() {
            throw new java.lang.UnsupportedOperationException();
        }
    }

    private static class UnmodifiableList extends UnmodifiableCollection implements List {
        final List list;

        UnmodifiableList(List list) {
            super(list);
            this.list = list;
        }

        public Object get(int index) {
            return list.get(index);
        }

        public int indexOf(Object o) {
            return list.indexOf(o);
        }

        public Object set(int index, Object element) {
            throw new java.lang.UnsupportedOperationException();
        }

        public Object remove(int index) {
            throw new java.lang.UnsupportedOperationException();
        }
    }

    private static class UnmodifiableSet extends UnmodifiableCollection implements Set {
        UnmodifiableSet(Set set) {
            super(set);
        }
    }

    private static class UnmodifiableMap implements Map {
        final Map m;

        UnmodifiableMap(Map m) {
            this.m = m;
        }

        public int size() {
            return m.size();
        }

        public boolean isEmpty() {
            return m.isEmpty();
        }

        public Object get(Object key) {
            return m.get(key);
        }

        public boolean containsKey(Object key) {
            return m.containsKey(key);
        }

        public boolean containsValue(Object value) {
            return m.containsValue(value);
        }

        public Set keySet() {
            return new UnmodifiableSet(m.keySet());
        }

        public Collection values() {
            return new UnmodifiableCollection(m.values());
        }

        public Set entrySet() {
            return new UnmodifiableSet(m.entrySet());
        }

        public Object put(Object key, Object value) {
            throw new java.lang.UnsupportedOperationException();
        }

        public Object remove(Object key) {
            throw new java.lang.UnsupportedOperationException();
        }

        public void clear() {
            throw new java.lang.UnsupportedOperationException();
        }
    }
}
