package io.quarkiverse.temporal.order.model.exceptions;

public class CustomerHistoryException extends RuntimeException {

    /**
     * Default
     */
    public CustomerHistoryException() {
        super();
    }

    /**
     * Takes the message
     *
     * @param message
     */
    public CustomerHistoryException(String message) {
        super(message);
    }

    /**
     * Takes a message and cause
     *
     * @param message
     * @param cause
     */
    public CustomerHistoryException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Takes the cause
     *
     * @param cause
     */
    public CustomerHistoryException(Throwable cause) {
        super(cause);
    }

}