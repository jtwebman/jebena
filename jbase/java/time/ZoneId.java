package java.time;

/**
 * Clean-room base type for a time-zone identifier. jbase models the concrete
 * {@link ZoneOffset} as the only zone in use; this type exists so that APIs
 * such as {@link Clock} can be typed against a zone the way the platform does.
 */
public abstract class ZoneId {

    protected ZoneId() {
    }

    public abstract String getId();

    public String toString() {
        return getId();
    }
}
