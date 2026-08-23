import java.math.BigInteger;

public class DiffBigInt2 {

    private static int checksum(String s) {
        int acc = 0;
        for (int i = 0; i < s.length(); i++) {
            acc = acc * 31 + s.charAt(i);
        }
        return acc;
    }

    public static int shiftLeft20() {
        return BigInteger.valueOf(1).shiftLeft(20).intValue();
    }

    public static int shiftRight3() {
        return BigInteger.valueOf(1024).shiftRight(3).intValue();
    }

    public static int testBit2() {
        return BigInteger.valueOf(12).testBit(2) ? 1 : 0;
    }

    public static int setBit0() {
        return BigInteger.valueOf(8).setBit(0).intValue();
    }

    public static int clearBit1() {
        return BigInteger.valueOf(15).clearBit(1).intValue();
    }

    public static int flipBit1() {
        return BigInteger.valueOf(5).flipBit(1).intValue();
    }

    public static int bitLength255() {
        return BigInteger.valueOf(255).bitLength();
    }

    public static int bitCount255() {
        return BigInteger.valueOf(255).bitCount();
    }

    public static int negBitLength() {
        return BigInteger.valueOf(-8).bitLength();
    }

    public static int andCase() {
        return BigInteger.valueOf(12).and(BigInteger.valueOf(10)).intValue();
    }

    public static int orCase() {
        return BigInteger.valueOf(12).or(BigInteger.valueOf(10)).intValue();
    }

    public static int xorCase() {
        return BigInteger.valueOf(12).xor(BigInteger.valueOf(10)).intValue();
    }

    public static int notCase() {
        return BigInteger.valueOf(5).not().intValue();
    }

    public static int modPowCase() {
        return BigInteger.valueOf(2).modPow(BigInteger.valueOf(10), BigInteger.valueOf(1000)).intValue();
    }

    public static int modInverseCase() {
        return BigInteger.valueOf(3).modInverse(BigInteger.valueOf(11)).intValue();
    }

    public static int prime17() {
        return BigInteger.valueOf(17).isProbablePrime(10) ? 1 : 0;
    }

    public static int composite21() {
        return BigInteger.valueOf(21).isProbablePrime(10) ? 1 : 0;
    }

    public static int sqrt144() {
        return BigInteger.valueOf(144).sqrt().intValue();
    }

    public static int bigShiftChecksum() {
        return checksum(BigInteger.valueOf(1).shiftLeft(100).toString());
    }
}
