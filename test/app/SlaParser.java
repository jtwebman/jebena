import java.text.NumberFormat;
import java.time.Duration;
import java.time.Period;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.stream.LongStream;

/**
 * An SLA-config parser exercising the newest clean-room jbase parse/format surface:
 *   - java.util.Scanner            : tokenize each config line
 *   - java.time.Duration.parse     : ISO-8601 window (PnDTnHnMnS)
 *   - java.time.Period.parse       : ISO-8601 retention (PnYnMnD)
 *   - java.text.NumberFormat       : grouped integer + decimal formatting
 *   - java.lang.Integer.parseInt(radix)/toHexString/toUnsignedString : hex mask field
 *   - java.util.TreeMap.ceilingEntry : request-rate -> pricing tier lookup
 * Output is plain text and must be byte-identical to real java.
 */
public class SlaParser {

    public static void main(String[] args) {
        String[] lines = {
            "gold 1200 ff PT4H30M P30D",
            "silver 800 3c PT2H P14D",
            "bronze 300 0a PT30M P7D",
            "platinum 2500 ff PT8H P90D",
        };

        TreeMap<Integer, String> tiers = new TreeMap<Integer, String>();
        tiers.put(500, "basic");
        tiers.put(1000, "standard");
        tiers.put(2000, "premium");

        NumberFormat intFmt = NumberFormat.getIntegerInstance();
        NumberFormat numFmt = NumberFormat.getInstance();

        long[] counts = new long[lines.length];
        long totalWindowMin = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            Scanner sc = new Scanner(lines[i]);
            String name = sc.next();
            int count = sc.nextInt();
            int mask = Integer.parseInt(sc.next(), 16);
            Duration w = Duration.parse(sc.next());
            Period r = Period.parse(sc.next());
            sc.close();

            counts[i] = count;
            long wmin = w.toMinutes();
            totalWindowMin += wmin;
            Map.Entry<Integer, String> te = tiers.ceilingEntry(count);
            String tier = (te == null) ? "enterprise" : te.getValue();

            sb.append(name)
                    .append(" count=").append(intFmt.format(count))
                    .append(" mask=0x").append(Integer.toHexString(mask))
                    .append(" umask=").append(Integer.toUnsignedString(mask))
                    .append(" windowMin=").append(wmin)
                    .append(" retDays=").append(r.getDays())
                    .append(" tier=").append(tier).append('\n');
        }

        long totalCount = LongStream.of(counts).sum();
        long maxCount = LongStream.of(counts).max().getAsLong();
        double avgWin = totalWindowMin / (double) lines.length;

        sb.append("totalCount=").append(intFmt.format(totalCount)).append('\n');
        sb.append("maxCount=").append(intFmt.format(maxCount)).append('\n');
        sb.append("totalWindowMin=").append(intFmt.format(totalWindowMin)).append('\n');
        sb.append("avgWindowMin=").append(numFmt.format(avgWin)).append('\n');
        sb.append("tierFor(1500)=").append(tiers.ceilingEntry(1500).getValue()).append('\n');

        System.out.print(sb.toString());
    }
}
