import java.math.BigInteger;
import java.util.Base64;
import java.util.HexFormat;
import java.util.IntSummaryStatistics;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.LongStream;

/**
 * A log-integrity / audit tool exercising the newest clean-room jbase surface together:
 *   - java.util.regex NAMED GROUPS (?<name>...) + Matcher.group(String)
 *   - java.util.Base64                : decode the signature field
 *   - java.math.BigInteger.modPow     : a deterministic per-record "digest"
 *   - java.util.HexFormat             : render the digest as hex
 *   - java.util.IntSummaryStatistics  : aggregate the amount field
 *   - java.util.stream.LongStream     : timestamp span
 * Output is plain text and must be byte-identical to real java.
 */
public class LogAudit {

    public static void main(String[] args) {
        String[] lines = {
            "user=alice ts=1700000000000 amt=1200 sig=YWxpY2U=",
            "user=bob ts=1700000005000 amt=3499 sig=Ym9i",
            "user=carol ts=1700000010000 amt=500 sig=Y2Fyb2w=",
            "user=dave ts=1700000060000 amt=8990 sig=ZGF2ZQ==",
            "malformed line without fields",
        };

        Pattern p = Pattern.compile(
                "user=(?<user>\\w+) ts=(?<ts>\\d+) amt=(?<amt>\\d+) sig=(?<sig>[A-Za-z0-9+/=]+)");
        Base64.Decoder dec = Base64.getDecoder();
        HexFormat hex = HexFormat.of();
        BigInteger e = BigInteger.valueOf(65537L);
        BigInteger m = new BigInteger("1000000007");

        IntSummaryStatistics stats = new IntSummaryStatistics();
        long[] ts = new long[lines.length];
        int kept = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            Matcher mt = p.matcher(lines[i]);
            if (!mt.find()) {
                sb.append("SKIP badline\n");
                continue;
            }
            String user = mt.group("user");
            long t = Long.parseLong(mt.group("ts"));
            int amt = Integer.parseInt(mt.group("amt"));
            byte[] sig = dec.decode(mt.group("sig"));

            long fold = 0L;
            for (int b = 0; b < sig.length; b++) {
                fold = fold * 31L + (sig[b] & 0xff);
            }
            long v = fold & 0x7fffffffffffffffL;
            BigInteger digest = BigInteger.valueOf(v).modPow(e, m);
            String dhex = hex.toHexDigits(digest.intValue());

            stats.accept(amt);
            ts[kept++] = t;
            sb.append(user).append(' ').append(t).append(' ').append(amt)
                    .append(' ').append(dhex).append('\n');
        }

        long[] tsKept = new long[kept];
        for (int i = 0; i < kept; i++) {
            tsKept[i] = ts[i];
        }
        long span = LongStream.of(tsKept).max().getAsLong()
                - LongStream.of(tsKept).min().getAsLong();

        sb.append("count=").append(stats.getCount()).append('\n');
        sb.append("sum=").append(stats.getSum())
                .append(" min=").append(stats.getMin())
                .append(" max=").append(stats.getMax()).append('\n');
        sb.append("avgX100=").append((long) (stats.getAverage() * 100)).append('\n');
        sb.append("tsSpanMs=").append(span).append('\n');

        System.out.print(sb.toString());
    }
}
