package net.cloudclient.pack;

/** Thrown when a {@code .cloudpack} cannot be parsed at all. */
public class PackFormatException extends Exception {

    private static final long serialVersionUID = 1L;

    public PackFormatException(String message) {
        super(message);
    }

    public PackFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
