package java.util;

/**
 * Clean-room java.util.StringJoiner. Joins CharSequence elements with a
 * delimiter, wrapped by an optional prefix and suffix. When empty, toString()
 * returns emptyValue (prefix+suffix unless overridden by setEmptyValue).
 */
public final class StringJoiner {
    private final String prefix;
    private final String delimiter;
    private final String suffix;

    // Accumulates elements joined by the delimiter, prefixed by prefix, but
    // without the suffix. Stays null until the first element is added.
    private StringBuilder value;

    // Value returned by toString() when no elements have been added. When null,
    // an empty joiner reports prefix+suffix.
    private String emptyValue;

    public StringJoiner(CharSequence delimiter) {
        this(delimiter, "", "");
    }

    public StringJoiner(CharSequence delimiter, CharSequence prefix, CharSequence suffix) {
        this.delimiter = delimiter.toString();
        this.prefix = prefix.toString();
        this.suffix = suffix.toString();
        this.value = null;
        this.emptyValue = this.prefix + this.suffix;
    }

    public StringJoiner setEmptyValue(CharSequence emptyValue) {
        this.emptyValue = emptyValue.toString();
        return this;
    }

    public StringJoiner add(CharSequence newElement) {
        prepareBuilder().append(newElement == null ? "null" : newElement.toString());
        return this;
    }

    public StringJoiner merge(StringJoiner other) {
        if (other.value != null) {
            // other.value begins with other's prefix; skip it so merge appends
            // only the joined content as a single element.
            String otherContent = other.value.toString();
            String withoutPrefix = otherContent.substring(other.prefix.length());
            prepareBuilder().append(withoutPrefix);
        }
        return this;
    }

    private StringBuilder prepareBuilder() {
        if (value != null) {
            value.append(delimiter);
        } else {
            value = new StringBuilder().append(prefix);
        }
        return value;
    }

    public String toString() {
        if (value == null) {
            return emptyValue;
        }
        if (suffix.equals("")) {
            return value.toString();
        }
        return value.toString() + suffix;
    }

    public int length() {
        if (value == null) {
            return emptyValue.length();
        }
        return value.length() + suffix.length();
    }
}
