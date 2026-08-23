import java.util.BitSet;

/**
 * Differential coverage for java.util.BitSet range/stream operations.
 * Each case returns a deterministic int: cardinality, checksum of set-bit
 * indices (acc = acc*31 + index), lengths, boolean-as-0/1, or a sentinel.
 */
public class DiffBitSet3 {

    private static int checksum(BitSet b) {
        int acc = 0;
        for (int i = b.nextSetBit(0); i >= 0; i = b.nextSetBit(i + 1)) {
            acc = acc * 31 + i;
        }
        return acc;
    }

    // get(from,to) returns a new BitSet subset -> cardinality/checksum
    static int getSubsetAligned() {
        BitSet b = new BitSet();
        b.set(64, 200);
        b.set(300);
        BitSet sub = b.get(64, 320); // word-aligned start
        return sub.cardinality() * 100000 + checksum(sub);
    }

    static int getSubsetUnaligned() {
        BitSet b = new BitSet();
        b.set(5, 30);
        b.set(70, 90);
        b.set(150);
        BitSet sub = b.get(7, 155); // shifts bits down by 7
        return sub.cardinality() * 100000 + checksum(sub) + sub.length();
    }

    static int getSubsetBeyondLength() {
        BitSet b = new BitSet();
        b.set(3);
        b.set(40);
        BitSet sub = b.get(10, 500); // toIndex clamps to length
        int empty = sub.isEmpty() ? 0 : 1;
        return sub.cardinality() * 1000 + checksum(sub) * 10 + empty;
    }

    // set(from,to) then cardinality
    static int setRangeMultiWord() {
        BitSet b = new BitSet();
        b.set(10, 10); // no-op
        b.set(60, 260);
        return b.cardinality() * 100000 + checksum(b) + b.length();
    }

    // clear(from,to) then cardinality
    static int clearRangeSpanning() {
        BitSet b = new BitSet();
        b.set(0, 256);
        b.clear(40, 200);
        b.clear(250, 400); // extends past length
        return b.cardinality() * 100000 + checksum(b) + b.length();
    }

    // flip(from,to) then cardinality
    static int flipRangeTwice() {
        BitSet b = new BitSet();
        b.set(0, 64);
        b.flip(32, 160); // flips across words
        b.flip(100, 300);
        return b.cardinality() * 100000 + checksum(b);
    }

    // stream() over a sparse set summing indices
    static int streamSumSparse() {
        BitSet b = new BitSet();
        b.set(2);
        b.set(63);
        b.set(64);
        b.set(199);
        b.set(1000);
        int sum = b.stream().sum();
        int count = (int) b.stream().count();
        return sum * 100 + count;
    }

    static int streamMapReduce() {
        BitSet b = new BitSet();
        b.set(1, 5);
        b.set(70);
        b.set(130);
        int acc = b.stream().map(x -> x * 2).reduce(7, (a, c) -> a * 31 + c);
        return acc;
    }

    // nextClearBit / previousSetBit / previousClearBit
    static int clearBitNavigation() {
        BitSet b = new BitSet();
        b.set(0, 128);
        b.clear(64);
        int acc = 0;
        acc = acc * 31 + b.nextClearBit(0);      // 64
        acc = acc * 31 + b.nextClearBit(64);     // 64
        acc = acc * 31 + b.nextClearBit(65);     // 128
        acc = acc * 31 + b.previousClearBit(127);// 64
        acc = acc * 31 + b.previousClearBit(63); // -1
        acc = acc * 31 + b.previousClearBit(200);// 200
        return acc;
    }

    static int setBitNavigation() {
        BitSet b = new BitSet();
        b.set(5);
        b.set(64);
        b.set(65);
        b.set(191);
        int acc = 0;
        acc = acc * 31 + b.previousSetBit(300); // 191
        acc = acc * 31 + b.previousSetBit(190); // 65
        acc = acc * 31 + b.previousSetBit(63);  // 5
        acc = acc * 31 + b.previousSetBit(4);   // -1
        acc = acc * 31 + b.nextClearBit(64);    // 66
        return acc;
    }

    // length after flips
    static int lengthAfterFlips() {
        BitSet b = new BitSet();
        b.flip(0, 200);
        int l1 = b.length();      // 200
        b.flip(150, 200);
        int l2 = b.length();      // 150
        b.flip(199);
        int l3 = b.length();      // 200
        b.clear();
        int l4 = b.length();      // 0
        return l1 * 1000000 + l2 * 1000 + l3 + l4;
    }

    // toString on a small set encoded via length + hash
    static int toStringEncoded() {
        BitSet b = new BitSet();
        b.set(2);
        b.set(4);
        b.set(64);
        b.set(200);
        String s = b.toString(); // "{2, 4, 64, 200}"
        return s.length() * 1000000 + (s.hashCode() & 0x7fffff);
    }
}
