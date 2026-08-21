public class Driver {
    public static void main(String[] a) throws Exception {
        var m = Class.forName(a[0]).getDeclaredMethod(a[1]);
        m.setAccessible(true);
        System.out.println(m.invoke(null));
    }
}
