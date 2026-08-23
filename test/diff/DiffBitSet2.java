import java.util.BitSet;

public class DiffBitSet2 {

    private static int checksumInts(int[] vals) {
        int acc = 1;
        for (int i = 0; i < vals.length; i++) {
            acc = acc * 31 + vals[i];
        }
        return acc;
    }

    // valueOf(0b1011) -> set bits {0,1,3}; cardinality + checksum of set bits
    public static int valueOfCardChecksum() {
        BitSet bs = BitSet.valueOf(new long[] { 0b1011L });
        int card = bs.cardinality();
        int[] bits = new int[card];
        int p = 0;
        for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
            bits[p++] = i;
        }
        return card + checksumInts(bits);
    }

    // valueOf with trailing zero word ignored
    public static int valueOfTrailingZero() {
        BitSet bs = BitSet.valueOf(new long[] { 0x8000000000000001L, 0L });
        int card = bs.cardinality();
        int len = bs.length();
        return card * 1000 + len;
    }

    // valueOf multi-word
    public static int valueOfMultiWord() {
        BitSet bs = BitSet.valueOf(new long[] { 0b101L, 0b11L });
        // bits: 0,2 (word0) and 64,65 (word1)
        int[] bits = new int[bs.cardinality()];
        int p = 0;
        for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
            bits[p++] = i;
        }
        return checksumInts(bits);
    }

    private static BitSet build() {
        BitSet bs = new BitSet();
        bs.set(1);
        bs.set(64);
        bs.set(65);
        bs.set(130);
        return bs;
    }

    // toLongArray length + checksum of longs cast to int
    public static int toLongArrayCheck() {
        BitSet bs = build();
        long[] longs = bs.toLongArray();
        int[] asInt = new int[longs.length];
        for (int i = 0; i < longs.length; i++) {
            asInt[i] = (int) longs[i];
        }
        return longs.length * 1000000 + checksumInts(asInt);
    }

    // round-trip: valueOf(toLongArray()) equals original
    public static int roundTrip() {
        BitSet bs = build();
        BitSet bs2 = BitSet.valueOf(bs.toLongArray());
        return bs.equals(bs2) ? 1 : 0;
    }

    // empty BitSet toLongArray length is 0
    public static int emptyToLongArray() {
        BitSet bs = new BitSet();
        return bs.toLongArray().length;
    }

    // stream().sum() of set-bit indices
    public static int streamSum() {
        BitSet bs = build();
        return bs.stream().sum(); // 1+64+65+130 = 260
    }

    // stream().count()
    public static int streamCount() {
        BitSet bs = build();
        return (int) bs.stream().count();
    }

    // stream on valueOf, filtered
    public static int streamFiltered() {
        BitSet bs = BitSet.valueOf(new long[] { 0b1011L });
        return bs.stream().filter(x -> x > 0).sum(); // 1+3 = 4
    }
}
