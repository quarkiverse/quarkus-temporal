package io.quarkiverse.temporal.order.model.exceptions;

public class OrderInvalidException extends RuntimeException {

    /**
     * Default
     */
    public OrderInvalidException() {
        super();
    }

    /**
     * Takes the message
     *
     * @param message
     */
    public OrderInvalidException(String message) {
        super(message);
    }

    /**
     * Takes a message and cause
     *
     * @param message
     * @param cause
     */
    public OrderInvalidException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Takes the cause
     *
     * @param cause
     */
    public OrderInvalidException(Throwable cause) {
        super(cause);
    }

}