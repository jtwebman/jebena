import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.stream.DoubleStream;
import java.util.stream.LongStream;
import java.util.zip.CRC32;

/**
 * A sales-log report exercising the newest clean-room jbase classes together end-to-end:
 *   - java.util.Base64            : decode a Base64 name token (and roundtrip re-encode)
 *   - java.time.format.DateTimeFormatter : reformat ISO dates to dd/MM/yyyy
 *   - java.util.stream.LongStream : total / max / min / average of the cent amounts
 *   - java.util.stream.DoubleStream : dollar average
 *   - java.util.zip.CRC32         : checksum of the rendered report
 * Each record is "base64Name|yyyy-MM-dd|amountCents". Output is plain text and must be
 * byte-identical to real java.
 */
public class SalesReport {

    public static void main(String[] args) {
        String[] lines = {
            "TGF1bmNo|2026-01-15|120050",   // Launch
            "UmVuZXdhbA==|2026-03-02|34999", // Renewal
            "Qm9udXM=|2026-07-04|500000",    // Bonus
            "RmVl|2026-11-28|8990",          // Fee
            "QXVkaXQ=|2026-02-16|74225",     // Audit
        };

        DateTimeFormatter out = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        Base64.Decoder dec = Base64.getDecoder();
        Base64.Encoder enc = Base64.getEncoder();

        long[] cents = new long[lines.length];
        StringBuilder report = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String[] p = lines[i].split("\\|");
            String name = new String(dec.decode(p[0]));
            String[] d = p[1].split("-");
            LocalDate date = LocalDate.of(Integer.parseInt(d[0]),
                    Integer.parseInt(d[1]), Integer.parseInt(d[2]));
            long amt = Long.parseLong(p[2]);
            cents[i] = amt;
            report.append(name).append(" | ").append(out.format(date))
                    .append(" | ").append(amt).append('\n');
        }

        long total = LongStream.of(cents).sum();
        long max = LongStream.of(cents).max().getAsLong();
        long min = LongStream.of(cents).min().getAsLong();
        long avgCents = (long) LongStream.of(cents).average().getAsDouble();

        double[] dollars = new double[cents.length];
        for (int i = 0; i < cents.length; i++) {
            dollars[i] = cents[i] / 100.0;
        }
        double dollarAvg = DoubleStream.of(dollars).average().getAsDouble();

        report.append("total=").append(total).append('\n');
        report.append("max=").append(max).append(" min=").append(min).append('\n');
        report.append("avgCents=").append(avgCents).append('\n');
        report.append("avgDollarsX100=").append((long) (dollarAvg * 100)).append('\n');

        CRC32 crc = new CRC32();
        crc.update(report.toString().getBytes());

        System.out.print(report.toString());
        System.out.println("crc32=" + crc.getValue());

        String first = lines[0].split("\\|")[0];
        String reenc = enc.encodeToString(dec.decode(first));
        System.out.println("reencodeMatch=" + reenc.equals(first));
    }
}
