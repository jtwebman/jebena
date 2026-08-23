package java.time.temporal;

/**
 * Clean-room minimal TemporalAccessor: the read-only supertype that the real JDK
 * date-time types implement. Jebena's date-time types are read via their concrete
 * getters, so this type carries no methods here; it exists so that bytecode compiled
 * against a real JDK — where DateTimeFormatter.format takes a TemporalAccessor and the
 * LocalDate/LocalDateTime arguments are TemporalAccessors — resolves against jbase.
 */
public interface TemporalAccessor {
}
