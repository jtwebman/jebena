public class DiffRandom {
    public static int newSeedInts() {
        java.util.Random r = new java.util.Random(42L);
        int acc = 0;
        for (int i = 0; i < 6; i++) {
            acc = acc * 31 + r.nextInt();
        }
        return acc;
    }

    public static int newSeedBounded() {
        java.util.Random r = new java.util.Random(42L);
        int acc = 0;
        for (int i = 0; i < 8; i++) {
            acc = acc * 31 + r.nextInt(100);
        }
        return acc;
    }

    public static int seedLong() {
        java.util.Random r = new java.util.Random(7L);
        long v = r.nextLong();
        return (int) v;
    }

    public static int seedBools() {
        java.util.Random r = new java.util.Random(1L);
        int count = 0;
        for (int i = 0; i < 20; i++) {
            if (r.nextBoolean()) count++;
        }
        return count;
    }

    public static int seedDouble() {
        java.util.Random r = new java.util.Random(3L);
        double d = r.nextDouble();
        return (int) (d * 1000000);
    }

    public static int seedFloat() {
        java.util.Random r = new java.util.Random(5L);
        float f = r.nextFloat();
        return (int) (f * 1000000);
    }

    public static int setSeedReset() {
        java.util.Random r = new java.util.Random(99L);
        int a = r.nextInt();
        int b = r.nextInt();
        r.setSeed(99L);
        int c = r.nextInt();
        int d = r.nextInt();
        return (a == c && b == d) ? 1 : 0;
    }

    public static int boundPowerOfTwo() {
        java.util.Random r = new java.util.Random(123L);
        int acc = 0;
        for (int i = 0; i < 8; i++) {
            acc = acc * 31 + r.nextInt(256);
        }
        return acc;
    }
}
