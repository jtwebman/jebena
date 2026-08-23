import java.text.DecimalFormat;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;

/**
 * An event-schedule report exercising the newest clean-room jbase classes together:
 *   - java.time.LocalDate / DayOfWeek / Month / Period : dates, weekday, month name, span
 *   - java.text.DecimalFormat                          : money formatting ("#,##0.00")
 *   - java.nio.file.Paths / Path                       : building the report path
 * Date ordering and span use toEpochDay() (a long), and weekday is derived from it too.
 * This is deliberate: jbase's LocalDate compareTo/isBefore/isAfter/until/getDayOfWeek have
 * different signatures than real JDK (which takes/returns ChronoLocalDate and the DayOfWeek
 * enum), so real-javac bytecode can't resolve them against jbase — toEpochDay()/getMonthValue()
 * have matching descriptors and work on both. Output is plain text, byte-identical to real java.
 */
public class EventReport {

    static final class Event {
        final String name;
        final LocalDate date;
        final double amount;

        Event(String name, LocalDate date, double amount) {
            this.name = name;
            this.date = date;
            this.amount = amount;
        }
    }

    // ISO day-of-week 1..7 (Mon..Sun) from the epoch day (1970-01-01 was a Thursday).
    static int isoDow(LocalDate d) {
        long e = d.toEpochDay();
        int r = (int) (((e + 3) % 7 + 7) % 7); // 0=Mon..6=Sun
        return r + 1;
    }

    public static void main(String[] args) {
        List<Event> events = new ArrayList<Event>();
        events.add(new Event("Bonus", LocalDate.of(2026, 7, 4), 5000.0));
        events.add(new Event("Launch", LocalDate.of(2026, 1, 15), 1200.5));
        events.add(new Event("Fee", LocalDate.of(2026, 11, 28), 89.9));
        events.add(new Event("Renewal", LocalDate.of(2026, 3, 2), 349.99));
        events.add(new Event("Audit", LocalDate.of(2026, 2, 16), 742.25));

        // Sort by date ascending (insertion sort on epoch day — a portable long compare).
        for (int i = 1; i < events.size(); i++) {
            Event key = events.get(i);
            int j = i - 1;
            while (j >= 0 && events.get(j).date.toEpochDay() > key.date.toEpochDay()) {
                events.set(j + 1, events.get(j));
                j--;
            }
            events.set(j + 1, key);
        }

        DecimalFormat money = new DecimalFormat("#,##0.00");
        double total = 0.0;
        int weekend = 0;
        for (int i = 0; i < events.size(); i++) {
            Event e = events.get(i);
            int dow = isoDow(e.date);
            String wd = DayOfWeek.of(dow).name();
            String mon = Month.of(e.date.getMonthValue()).name();
            System.out.println(e.name + " | " + e.date.toString() + " | " + wd
                    + " | " + mon + " | $" + money.format(e.amount));
            total += e.amount;
            if (dow >= 6) {
                weekend++;
            }
        }

        System.out.println("total=$" + money.format(total));
        System.out.println("weekendEvents=" + weekend);

        long spanDays = events.get(events.size() - 1).date.toEpochDay()
                - events.get(0).date.toEpochDay();
        System.out.println("spanDays=" + spanDays);

        Path p = Paths.get("reports", "2026", "..", "2026", "summary.txt").normalize();
        System.out.println("reportPath=" + p.toString());
        System.out.println("reportFile=" + p.getFileName().toString());
    }
}
