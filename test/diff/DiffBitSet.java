import java.util.BitSet;

/**
 * Differential coverage for java.util.BitSet. Each case encodes a BitSet result
 * as an int: either cardinality, length, a boolean (0/1), or a rolling checksum
 * of set-bit indices (acc = acc*31 + index) so ordering/values are compared
 * byte-for-byte against the real JDK.
 */
public class DiffBitSet {

    private static int checksum(BitSet b) {
        int acc = 0;
        for (int i = b.nextSetBit(0); i >= 0; i = b.nextSetBit(i + 1)) {
            acc = acc * 31 + i;
        }
        return acc;
    }

    static int setAndCardinality() {
        BitSet b = new BitSet();
        b.set(0);
        b.set(63);
        b.set(64);
        b.set(200);
        b.set(200); // idempotent
        return b.cardinality() * 100000 + checksum(b);
    }

    static int setRangeAndClear() {
        BitSet b = new BitSet(16);
        b.set(4, 70); // spans word boundary at 64
        b.clear(10, 20);
        b.clear(65);
        return b.cardinality() * 100000 + checksum(b) + b.length();
    }

    static int flipBehavior() {
        BitSet b = new BitSet();
        b.flip(5);
        b.flip(5); // back to empty at 5
        b.flip(0, 130); // flip a wide range
        b.flip(64);
        int e = b.isEmpty() ? 1 : 0;
        return b.cardinality() * 1000 + checksum(b) + e;
    }

    static int lengthAndSize() {
        BitSet b = new BitSet(64);
        b.set(0);
        b.set(129);
        int len = b.length(); // 130
        b.clear(129);
        int len2 = b.length(); // 1
        return len * 1000 + len2 * 10 + (b.isEmpty() ? 0 : 1);
    }

    static int nextSetAndClear() {
        BitSet b = new BitSet();
        b.set(1);
        b.set(2);
        b.set(64);
        b.set(65);
        int acc = 0;
        acc = acc * 31 + b.nextSetBit(0);   // 1
        acc = acc * 31 + b.nextSetBit(3);   // 64
        acc = acc * 31 + b.nextClearBit(1); // 0
        acc = acc * 31 + b.nextClearBit(64);// 66
        acc = acc * 31 + b.previousSetBit(63); // 2
        acc = acc * 31 + b.previousSetBit(200);// 65
        acc = acc * 31 + b.nextSetBit(66);  // -1
        return acc;
    }

    static int andOp() {
        BitSet a = new BitSet();
        a.set(0, 100);
        BitSet b = new BitSet();
        b.set(50, 150);
        a.and(b);
        return a.cardinality() * 100000 + checksum(a);
    }

    static int orOp() {
        BitSet a = new BitSet();
        a.set(0);
        a.set(65);
        BitSet b = new BitSet();
        b.set(1);
        b.set(200);
        a.or(b);
        return a.cardinality() * 100000 + checksum(a);
    }

    static int xorOp() {
        BitSet a = new BitSet();
        a.set(0, 10);
        BitSet b = new BitSet();
        b.set(5, 15);
        a.xor(b);
        return a.cardinality() * 1000 + checksum(a);
    }

    static int andNotOp() {
        BitSet a = new BitSet();
        a.set(0, 128);
        BitSet b = new BitSet();
        b.set(0, 64);
        a.andNot(b);
        return a.cardinality() * 100000 + checksum(a);
    }

    static int intersectsAndEquals() {
        BitSet a = new BitSet();
        a.set(1);
        a.set(70);
        BitSet b = new BitSet();
        b.set(70);
        BitSet c = new BitSet();
        c.set(5);
        int i1 = a.intersects(b) ? 1 : 0; // 1
        int i2 = a.intersects(c) ? 1 : 0; // 0
        BitSet d = new BitSet();
        d.set(1);
        d.set(70);
        int eq = a.equals(d) ? 1 : 0;     // 1
        int eq2 = a.equals(b) ? 1 : 0;    // 0
        return i1 * 1000 + i2 * 100 + eq * 10 + eq2;
    }

    static int toStringLen() {
        BitSet b = new BitSet();
        b.set(1);
        b.set(3);
        b.set(5);
        String s = b.toString(); // "{1, 3, 5}"
        return s.equals("{1, 3, 5}") ? s.length() : -1; // 9
    }

    static int getSubset() {
        BitSet b = new BitSet();
        b.set(10, 20);
        b.set(70);
        BitSet sub = b.get(15, 71); // bits 15..70 shifted down
        return sub.cardinality() * 1000 + checksum(sub);
    }
}
