package jebena;

public class OutDriver {
    public static void main(String[] a) throws Exception {
        Class.forName(a[0]).getDeclaredMethod(a[1]).invoke(null);
    }
}
