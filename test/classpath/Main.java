package cp;

// Entry class for the lazy-classpath smoke. Only this class is named on the
// jebena command line; Helper and Accumulator are loaded lazily by name from
// the classpath directory on first resolution.
public class Main {
    public static int entry() {
        int r = 0;
        r += Helper.square(7);            // 49
        r += Helper.cube(3);              // 27
        Accumulator acc = new Accumulator();
        for (int i = 1; i <= 10; i++) acc.add(i);
        r += acc.total();                 // 55
        r += new Helper(100).offsetBy(5); // 105
        r += Suit.values().length;        // 4
        r += Suit.SPADES.ordinal();       // 3
        r += (Suit.valueOf("HEARTS") == Suit.HEARTS) ? 2 : 0;  // 2
        return r;                         // 245
    }
}
