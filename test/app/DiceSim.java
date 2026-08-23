import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.IntSummaryStatistics;
import java.util.SplittableRandom;

/**
 * A deterministic Monte-Carlo / event simulator showcasing the newest clean-room classes:
 *   - java.util.SplittableRandom(fixedSeed) : reproducible (bit-exact SplitMix64) event stream
 *   - java.time.Instant + Clock.fixed       : a virtual clock advanced by plusMillis
 *   - java.util.IntSummaryStatistics         : aggregate event values
 * Rolls a 6-sided die 1000 times, tallies faces + categories, summarizes a value stream, and
 * advances a virtual clock by random deltas. Output is plain text, byte-identical to real java
 * (which requires SplittableRandom to match the JDK bit-for-bit).
 */
public class DiceSim {

    public static void main(String[] args) {
        SplittableRandom rng = new SplittableRandom(2026L);
        int n = 1000;

        Clock clock = Clock.fixed(Instant.ofEpochSecond(1700000000L), ZoneOffset.UTC);
        Instant t = clock.instant();

        int[] faceCount = new int[7]; // indices 1..6
        IntSummaryStatistics stats = new IntSummaryStatistics();
        int low = 0;
        int mid = 0;
        int high = 0;

        for (int i = 0; i < n; i++) {
            int roll = rng.nextInt(6) + 1;
            faceCount[roll]++;
            int value = rng.nextInt(100);
            stats.accept(value);
            if (roll <= 2) {
                low++;
            } else if (roll <= 4) {
                mid++;
            } else {
                high++;
            }
            t = t.plusMillis(rng.nextInt(1000));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("clockStartMs=").append(clock.millis()).append('\n');
        sb.append("rolls=").append(n).append('\n');
        for (int f = 1; f <= 6; f++) {
            sb.append("face").append(f).append('=').append(faceCount[f]).append('\n');
        }
        sb.append("valueSum=").append(stats.getSum())
                .append(" min=").append(stats.getMin())
                .append(" max=").append(stats.getMax())
                .append(" avgX1000=").append((long) (stats.getAverage() * 1000)).append('\n');
        sb.append("low=").append(low).append(" mid=").append(mid)
                .append(" high=").append(high).append('\n');
        sb.append("finalEpochMs=").append(t.toEpochMilli()).append('\n');
        sb.append("finalInstant=").append(t.toString()).append('\n');

        System.out.print(sb.toString());
    }
}
