import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.IntSummaryStatistics;
import java.util.stream.LongStream;

/**
 * A year-planner report exercising the newest clean-room java.time value types together:
 *   - java.time.Year          : leap / length
 *   - java.time.YearMonth     : per-month length + end-of-month
 *   - java.time.MonthDay      : recurring anniversaries (isValidYear / atYear)
 *   - java.time.OffsetDateTime: a scheduled event (toEpochSecond + ISO toString)
 *   - java.time.format.DateTimeFormatter : render the event
 *   - java.util.IntSummaryStatistics / LongStream : aggregate month lengths
 * Output is plain text and must be byte-identical to real java.
 */
public class YearPlanner {

    public static void main(String[] args) {
        int y = 2026;
        Year yr = Year.of(y);
        StringBuilder sb = new StringBuilder();
        sb.append("year=").append(yr.getValue())
                .append(" leap=").append(yr.isLeap())
                .append(" length=").append(yr.length()).append('\n');

        long[] lens = new long[12];
        IntSummaryStatistics stats = new IntSummaryStatistics();
        for (int m = 1; m <= 12; m++) {
            YearMonth ym = YearMonth.of(y, m);
            int len = ym.lengthOfMonth();
            lens[m - 1] = len;
            stats.accept(len);
            LocalDate end = ym.atEndOfMonth();
            sb.append(ym.toString()).append(" days=").append(len)
                    .append(" end=").append(end.toString()).append('\n');
        }
        long total = LongStream.of(lens).sum();
        sb.append("monthLenSum=").append(total)
                .append(" min=").append(stats.getMin())
                .append(" max=").append(stats.getMax())
                .append(" avgX100=").append((long) (stats.getAverage() * 100)).append('\n');

        MonthDay[] anns = { MonthDay.of(2, 29), MonthDay.of(7, 4), MonthDay.of(12, 25) };
        for (int i = 0; i < anns.length; i++) {
            MonthDay md = anns[i];
            boolean valid = md.isValidYear(y);
            sb.append(md.toString()).append(" valid=").append(valid);
            if (valid) {
                sb.append(" dayOfYear=").append(md.atYear(y).getDayOfYear());
            }
            sb.append('\n');
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        OffsetDateTime evt = OffsetDateTime.of(
                LocalDateTime.of(y, 3, 9, 14, 30, 0), ZoneOffset.ofHours(-5));
        sb.append("event=").append(fmt.format(evt.toLocalDateTime()))
                .append(evt.getOffset().getId())
                .append(" epoch=").append(evt.toEpochSecond()).append('\n');
        sb.append("eventIso=").append(evt.toString()).append('\n');

        System.out.print(sb.toString());
    }
}
