package io.quarkiverse.temporal.order.model.exceptions;

public class OrderNotFoundException extends RuntimeException {

    /**
     * Default
     */
    public OrderNotFoundException() {
        super();
    }

    /**
     * Takes the message
     *
     * @param message
     */
    public OrderNotFoundException(String message) {
        super(message);
    }

    /**
     * Takes a message and cause
     *
     * @param message
     * @param cause
     */
    public OrderNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Takes the cause
     *
     * @param cause
     */
    public OrderNotFoundException(Throwable cause) {
        super(cause);
    }

}