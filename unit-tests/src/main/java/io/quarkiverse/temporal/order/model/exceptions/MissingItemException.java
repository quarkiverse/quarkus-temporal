package io.quarkiverse.temporal.order.model.exceptions;

public class MissingItemException extends RuntimeException {

    /**
     * Default
     */
    public MissingItemException() {
        super();
    }

    /**
     * Takes the message
     *
     * @param message
     */
    public MissingItemException(String message) {
        super(message);
    }

    /**
     * Takes a message and cause
     *
     * @param message
     * @param cause
     */
    public MissingItemException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Takes the cause
     *
     * @param cause
     */
    public MissingItemException(Throwable cause) {
        super(cause);
    }

}