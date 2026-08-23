import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DiffDtf {

    private static int checksum(String s) {
        int acc = 0;
        for (int i = 0; i < s.length(); i++) {
            acc = acc * 31 + s.charAt(i);
        }
        return acc;
    }

    public static int isoDate() {
        String s = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .format(LocalDate.of(2026, 1, 5));
        return checksum(s);
    }

    public static int slashDate() {
        String s = DateTimeFormatter.ofPattern("dd/MM/yyyy")
                .format(LocalDate.of(2026, 1, 5));
        return checksum(s);
    }

    public static int dateTime() {
        String s = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .format(LocalDateTime.of(2026, 3, 9, 7, 4, 2));
        return checksum(s);
    }

    public static int literalHeavy() {
        // Only non-letter separators: real DateTimeFormatter treats any unquoted
        // letter as a reserved pattern symbol, so literals must avoid letters.
        String s = DateTimeFormatter.ofPattern("yyyy...MM...dd!!! (dd/MM)")
                .format(LocalDate.of(2020, 12, 31));
        return checksum(s);
    }

    public static int midnightTime() {
        String s = DateTimeFormatter.ofPattern("HH:mm:ss")
                .format(LocalDateTime.of(2000, 1, 1, 0, 0, 0));
        return checksum(s);
    }

    public static int endOfDay() {
        String s = DateTimeFormatter.ofPattern("HH:mm:ss")
                .format(LocalDateTime.of(2000, 1, 1, 23, 59, 59));
        return checksum(s);
    }

    public static int compactNoSep() {
        String s = DateTimeFormatter.ofPattern("yyyyMMdd")
                .format(LocalDate.of(2026, 8, 23));
        return checksum(s);
    }

    public static int singleLetters() {
        // single-letter runs: no zero padding
        String s = DateTimeFormatter.ofPattern("y-M-d")
                .format(LocalDate.of(2026, 1, 5));
        return checksum(s);
    }

    public static int fullDateTime() {
        String s = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
                .format(LocalDateTime.of(1999, 11, 8, 6, 5, 4));
        return checksum(s);
    }
}
