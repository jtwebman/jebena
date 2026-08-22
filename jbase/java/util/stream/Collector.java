package java.util.stream;

import java.util.ArrayList;

/**
 * A simplified, single-method collector. Given the stream's fully-realized
 * element list, produce the collected result. This is intentionally NOT the
 * real three-function {@code Collector}; a single reduce-the-whole-list method
 * is enough for our eager pipeline.
 */
public interface Collector {
    Object collect(ArrayList data);
}
