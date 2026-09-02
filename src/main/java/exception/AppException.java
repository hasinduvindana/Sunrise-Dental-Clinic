package exception;

/**
 * Base class for every error the application raises on purpose.
 * Each subclass maps onto one HTTP status code, which lets the servlet layer
 * translate business failures into responses without a chain of if-statements.
 */
public class AppException extends RuntimeException {

    private final int statusCode;

    public AppException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
