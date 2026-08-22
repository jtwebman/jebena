package java.net;

/**
 * Clean-room minimal java.net.InetAddress. Enough to name a host for Socket to
 * connect to; the native connect parses the textual address. No DNS resolution
 * beyond passing the literal through (getByName stores the text; a numeric
 * 127.0.0.1 / ::1 is what the native accepts today).
 */
public class InetAddress {
    private final String host;

    InetAddress(String host) {
        this.host = host;
    }

    public String getHostAddress() {
        return host;
    }

    public String getHostName() {
        return host;
    }

    public static InetAddress getByName(String host) {
        if (host == null || host.length() == 0) {
            return getLoopbackAddress();
        }
        return new InetAddress(host);
    }

    public static InetAddress getLoopbackAddress() {
        return new InetAddress("127.0.0.1");
    }

    public String toString() {
        return host;
    }
}
