package java.time.chrono;

/**
 * Clean-room minimal ChronoLocalDate: the calendar-neutral date supertype that real
 * JDK date types implement. Jebena only implements the ISO LocalDate, but the type must
 * exist with these signatures so that bytecode compiled against a real JDK — where
 * LocalDate.compareTo/isBefore/isAfter/isEqual/until take ChronoLocalDate — resolves
 * against our jbase LocalDate. Extends Comparable&lt;ChronoLocalDate&gt; to match the JDK.
 */
public interface ChronoLocalDate extends Comparable<ChronoLocalDate> {

    long toEpochDay();

    int compareTo(ChronoLocalDate other);

    boolean isBefore(ChronoLocalDate other);

    boolean isAfter(ChronoLocalDate other);

    boolean isEqual(ChronoLocalDate other);
}
