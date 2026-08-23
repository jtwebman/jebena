package java.util;

/**
 * A clean-room implementation of a vector of bits backed by a long[].
 * Bit index i lives in word i>>6, at bit position i&63.
 */
public class BitSet {

    private static final int ADDRESS_BITS_PER_WORD = 6;
    private static final int BITS_PER_WORD = 1 << ADDRESS_BITS_PER_WORD; // 64

    private static int numberOfTrailingZerosLong(long v) {
        if (v == 0) {
            return 64;
        }
        int low = (int) v;
        if (low != 0) {
            return Integer.numberOfTrailingZeros(low);
        }
        return 32 + Integer.numberOfTrailingZeros((int) (v >>> 32));
    }

    private static int numberOfLeadingZerosLong(long v) {
        if (v == 0) {
            return 64;
        }
        int high = (int) (v >>> 32);
        if (high != 0) {
            return Integer.numberOfLeadingZeros(high);
        }
        return 32 + Integer.numberOfLeadingZeros((int) v);
    }

    // words in use = number of words with a nonzero value, kept "clean".
    private long[] words;
    private int wordsInUse;

    public BitSet() {
        words = new long[1];
        wordsInUse = 0;
    }

    public BitSet(int nbits) {
        if (nbits < 0) {
            throw new NegativeArraySizeException("nbits < 0: " + nbits);
        }
        int n = wordIndex(nbits - 1) + 1;
        if (n < 1) {
            n = 1;
        }
        words = new long[n];
        wordsInUse = 0;
    }

    private static int wordIndex(int bitIndex) {
        return bitIndex >> ADDRESS_BITS_PER_WORD;
    }

    public static BitSet valueOf(long[] longs) {
        int n = longs.length;
        while (n > 0 && longs[n - 1] == 0) {
            n--;
        }
        BitSet bs = new BitSet();
        if (n > 0) {
            bs.words = new long[n];
            System.arraycopy(longs, 0, bs.words, 0, n);
        }
        bs.wordsInUse = n;
        return bs;
    }

    public long[] toLongArray() {
        long[] result = new long[wordsInUse];
        System.arraycopy(words, 0, result, 0, wordsInUse);
        return result;
    }

    public java.util.stream.IntStream stream() {
        int[] indices = new int[cardinality()];
        int p = 0;
        for (int i = nextSetBit(0); i >= 0; i = nextSetBit(i + 1)) {
            indices[p++] = i;
        }
        return java.util.stream.IntStream.of(indices);
    }

    private void checkIndex(int bitIndex) {
        if (bitIndex < 0) {
            throw new IndexOutOfBoundsException("bitIndex < 0: " + bitIndex);
        }
    }

    private void checkRange(int fromIndex, int toIndex) {
        if (fromIndex < 0) {
            throw new IndexOutOfBoundsException("fromIndex < 0: " + fromIndex);
        }
        if (toIndex < 0) {
            throw new IndexOutOfBoundsException("toIndex < 0: " + toIndex);
        }
        if (fromIndex > toIndex) {
            throw new IndexOutOfBoundsException("fromIndex: " + fromIndex
                    + " > toIndex: " + toIndex);
        }
    }

    private void ensureCapacity(int wordsRequired) {
        if (words.length < wordsRequired) {
            int newLen = words.length * 2;
            if (newLen < wordsRequired) {
                newLen = wordsRequired;
            }
            long[] grown = new long[newLen];
            System.arraycopy(words, 0, grown, 0, words.length);
            words = grown;
        }
    }

    // Ensure the word at wordIndex exists and update wordsInUse if we extend.
    private void expandTo(int wordIndex) {
        int wordsRequired = wordIndex + 1;
        if (wordsInUse < wordsRequired) {
            ensureCapacity(wordsRequired);
            wordsInUse = wordsRequired;
        }
    }

    // Drop trailing zero words so wordsInUse is exact.
    private void recalculateWordsInUse() {
        int i;
        for (i = wordsInUse - 1; i >= 0; i--) {
            if (words[i] != 0) {
                break;
            }
        }
        wordsInUse = i + 1;
    }

    public void set(int bitIndex) {
        checkIndex(bitIndex);
        int wordIndex = wordIndex(bitIndex);
        expandTo(wordIndex);
        words[wordIndex] |= (1L << bitIndex);
    }

    public void set(int bitIndex, boolean value) {
        if (value) {
            set(bitIndex);
        } else {
            clear(bitIndex);
        }
    }

    public void set(int fromIndex, int toIndex) {
        checkRange(fromIndex, toIndex);
        if (fromIndex == toIndex) {
            return;
        }
        int startWordIndex = wordIndex(fromIndex);
        int endWordIndex = wordIndex(toIndex - 1);
        expandTo(endWordIndex);

        long firstWordMask = (-1L) << fromIndex;
        long lastWordMask = (-1L) >>> -toIndex;
        if (startWordIndex == endWordIndex) {
            words[startWordIndex] |= (firstWordMask & lastWordMask);
        } else {
            words[startWordIndex] |= firstWordMask;
            for (int i = startWordIndex + 1; i < endWordIndex; i++) {
                words[i] = -1L;
            }
            words[endWordIndex] |= lastWordMask;
        }
    }

    public void set(int fromIndex, int toIndex, boolean value) {
        if (value) {
            set(fromIndex, toIndex);
        } else {
            clear(fromIndex, toIndex);
        }
    }

    public void clear(int bitIndex) {
        checkIndex(bitIndex);
        int wordIndex = wordIndex(bitIndex);
        if (wordIndex >= wordsInUse) {
            return;
        }
        words[wordIndex] &= ~(1L << bitIndex);
        recalculateWordsInUse();
    }

    public void clear(int fromIndex, int toIndex) {
        checkRange(fromIndex, toIndex);
        if (fromIndex == toIndex) {
            return;
        }
        int startWordIndex = wordIndex(fromIndex);
        if (startWordIndex >= wordsInUse) {
            return;
        }
        int endWordIndex = wordIndex(toIndex - 1);
        if (endWordIndex >= wordsInUse) {
            toIndex = length();
            endWordIndex = wordsInUse - 1;
        }

        long firstWordMask = (-1L) << fromIndex;
        long lastWordMask = (-1L) >>> -toIndex;
        if (startWordIndex == endWordIndex) {
            words[startWordIndex] &= ~(firstWordMask & lastWordMask);
        } else {
            words[startWordIndex] &= ~firstWordMask;
            for (int i = startWordIndex + 1; i < endWordIndex; i++) {
                words[i] = 0;
            }
            words[endWordIndex] &= ~lastWordMask;
        }
        recalculateWordsInUse();
    }

    public void clear() {
        while (wordsInUse > 0) {
            words[--wordsInUse] = 0;
        }
    }

    public void flip(int bitIndex) {
        checkIndex(bitIndex);
        int wordIndex = wordIndex(bitIndex);
        expandTo(wordIndex);
        words[wordIndex] ^= (1L << bitIndex);
        recalculateWordsInUse();
    }

    public void flip(int fromIndex, int toIndex) {
        checkRange(fromIndex, toIndex);
        if (fromIndex == toIndex) {
            return;
        }
        int startWordIndex = wordIndex(fromIndex);
        int endWordIndex = wordIndex(toIndex - 1);
        expandTo(endWordIndex);

        long firstWordMask = (-1L) << fromIndex;
        long lastWordMask = (-1L) >>> -toIndex;
        if (startWordIndex == endWordIndex) {
            words[startWordIndex] ^= (firstWordMask & lastWordMask);
        } else {
            words[startWordIndex] ^= firstWordMask;
            for (int i = startWordIndex + 1; i < endWordIndex; i++) {
                words[i] ^= -1L;
            }
            words[endWordIndex] ^= lastWordMask;
        }
        recalculateWordsInUse();
    }

    public boolean get(int bitIndex) {
        checkIndex(bitIndex);
        int wordIndex = wordIndex(bitIndex);
        return (wordIndex < wordsInUse)
                && ((words[wordIndex] & (1L << bitIndex)) != 0);
    }

    public BitSet get(int fromIndex, int toIndex) {
        checkRange(fromIndex, toIndex);
        int len = length();
        if (len <= fromIndex || fromIndex == toIndex) {
            return new BitSet(0);
        }
        if (toIndex > len) {
            toIndex = len;
        }

        BitSet result = new BitSet(toIndex - fromIndex);
        int targetWords = wordIndex(toIndex - fromIndex - 1) + 1;
        int sourceIndex = wordIndex(fromIndex);
        boolean wordAligned = ((fromIndex & (BITS_PER_WORD - 1)) == 0);

        int i;
        for (i = 0; i < targetWords - 1; i++, sourceIndex++) {
            result.words[i] = wordAligned ? words[sourceIndex]
                    : (words[sourceIndex] >>> fromIndex)
                            | (words[sourceIndex + 1] << -fromIndex);
        }
        long lastWordMask = (-1L) >>> -toIndex;
        result.words[i] = ((toIndex - 1) & (BITS_PER_WORD - 1)) < (fromIndex & (BITS_PER_WORD - 1))
                ? ((words[sourceIndex] >>> fromIndex)
                        | ((words[sourceIndex + 1] & lastWordMask) << -fromIndex))
                : ((words[sourceIndex] & lastWordMask) >>> fromIndex);

        result.wordsInUse = targetWords;
        result.recalculateWordsInUse();
        return result;
    }

    public int cardinality() {
        int sum = 0;
        for (int i = 0; i < wordsInUse; i++) {
            sum += Long.bitCount(words[i]);
        }
        return sum;
    }

    public int length() {
        if (wordsInUse == 0) {
            return 0;
        }
        return BITS_PER_WORD * (wordsInUse - 1)
                + (BITS_PER_WORD - numberOfLeadingZerosLong(words[wordsInUse - 1]));
    }

    public int size() {
        return words.length * BITS_PER_WORD;
    }

    public boolean isEmpty() {
        return wordsInUse == 0;
    }

    public int nextSetBit(int fromIndex) {
        checkIndex(fromIndex);
        int u = wordIndex(fromIndex);
        if (u >= wordsInUse) {
            return -1;
        }
        long word = words[u] & ((-1L) << fromIndex);
        while (true) {
            if (word != 0) {
                return (u * BITS_PER_WORD) + numberOfTrailingZerosLong(word);
            }
            if (++u == wordsInUse) {
                return -1;
            }
            word = words[u];
        }
    }

    public int nextClearBit(int fromIndex) {
        checkIndex(fromIndex);
        int u = wordIndex(fromIndex);
        if (u >= wordsInUse) {
            return fromIndex;
        }
        long word = ~words[u] & ((-1L) << fromIndex);
        while (true) {
            if (word != 0) {
                return (u * BITS_PER_WORD) + numberOfTrailingZerosLong(word);
            }
            if (++u == wordsInUse) {
                return wordsInUse * BITS_PER_WORD;
            }
            word = ~words[u];
        }
    }

    public int previousSetBit(int fromIndex) {
        if (fromIndex < 0) {
            if (fromIndex == -1) {
                return -1;
            }
            throw new IndexOutOfBoundsException("fromIndex < -1: " + fromIndex);
        }
        int u = wordIndex(fromIndex);
        if (u >= wordsInUse) {
            return length() - 1;
        }
        long word = words[u] & ((-1L) >>> -(fromIndex + 1));
        while (true) {
            if (word != 0) {
                return (u + 1) * BITS_PER_WORD - 1 - numberOfLeadingZerosLong(word);
            }
            if (u-- == 0) {
                return -1;
            }
            word = words[u];
        }
    }

    public int previousClearBit(int fromIndex) {
        if (fromIndex < 0) {
            if (fromIndex == -1) {
                return -1;
            }
            throw new IndexOutOfBoundsException("fromIndex < -1: " + fromIndex);
        }
        int u = wordIndex(fromIndex);
        if (u >= wordsInUse) {
            return fromIndex;
        }
        long word = ~words[u] & ((-1L) >>> -(fromIndex + 1));
        while (true) {
            if (word != 0) {
                return (u + 1) * BITS_PER_WORD - 1 - numberOfLeadingZerosLong(word);
            }
            if (u-- == 0) {
                return -1;
            }
            word = ~words[u];
        }
    }

    public void and(BitSet set) {
        if (this == set) {
            return;
        }
        while (wordsInUse > set.wordsInUse) {
            words[--wordsInUse] = 0;
        }
        for (int i = 0; i < wordsInUse; i++) {
            words[i] &= set.words[i];
        }
        recalculateWordsInUse();
    }

    public void or(BitSet set) {
        if (this == set) {
            return;
        }
        int wordsInCommon = Math.min(wordsInUse, set.wordsInUse);
        if (wordsInUse < set.wordsInUse) {
            ensureCapacity(set.wordsInUse);
            wordsInUse = set.wordsInUse;
        }
        for (int i = 0; i < wordsInCommon; i++) {
            words[i] |= set.words[i];
        }
        if (wordsInCommon < set.wordsInUse) {
            System.arraycopy(set.words, wordsInCommon, words, wordsInCommon,
                    set.wordsInUse - wordsInCommon);
        }
    }

    public void xor(BitSet set) {
        int wordsInCommon = Math.min(wordsInUse, set.wordsInUse);
        if (wordsInUse < set.wordsInUse) {
            ensureCapacity(set.wordsInUse);
            wordsInUse = set.wordsInUse;
        }
        for (int i = 0; i < wordsInCommon; i++) {
            words[i] ^= set.words[i];
        }
        if (wordsInCommon < set.wordsInUse) {
            System.arraycopy(set.words, wordsInCommon, words, wordsInCommon,
                    set.wordsInUse - wordsInCommon);
        }
        recalculateWordsInUse();
    }

    public void andNot(BitSet set) {
        int common = Math.min(wordsInUse, set.wordsInUse);
        for (int i = 0; i < common; i++) {
            words[i] &= ~set.words[i];
        }
        recalculateWordsInUse();
    }

    public boolean intersects(BitSet set) {
        int common = Math.min(wordsInUse, set.wordsInUse);
        for (int i = common - 1; i >= 0; i--) {
            if ((words[i] & set.words[i]) != 0) {
                return true;
            }
        }
        return false;
    }

    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append('{');
        int first = nextSetBit(0);
        if (first != -1) {
            b.append(first);
            for (int i = nextSetBit(first + 1); i >= 0; i = nextSetBit(i + 1)) {
                b.append(", ");
                b.append(i);
            }
        }
        b.append('}');
        return b.toString();
    }

    public int hashCode() {
        long h = 1234;
        for (int i = wordsInUse; --i >= 0;) {
            h ^= words[i] * (i + 1);
        }
        return (int) ((h >> 32) ^ h);
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof BitSet)) {
            return false;
        }
        BitSet set = (BitSet) obj;
        if (wordsInUse != set.wordsInUse) {
            return false;
        }
        for (int i = 0; i < wordsInUse; i++) {
            if (words[i] != set.words[i]) {
                return false;
            }
        }
        return true;
    }
}
