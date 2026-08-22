package st;
import java.util.ArrayDeque;
public class ArrayDequeStress {
  // interleaved add/remove forcing circular wrap + growth; count must stay exact
  public static int demo() {
    ArrayDeque d = new ArrayDeque();
    int got = 0;
    for (int r=0;r<100;r++) { d.addLast(Integer.valueOf(1)); if (!d.isEmpty()) { d.removeFirst(); got++; } }
    // now flood 50 then drain
    for (int i=0;i<50;i++) d.addLast(Integer.valueOf(1));
    while (!d.isEmpty()) { d.removeFirst(); got++; }
    return got; // 100 + 50 = 150
  }
  public static int grow() {
    ArrayDeque d = new ArrayDeque();
    for (int i=0;i<100;i++) d.addLast(Integer.valueOf(1));  // force growth past default 16
    int got=0; while(!d.isEmpty()){ d.removeFirst(); got++; }
    return got; // 100
  }
}
