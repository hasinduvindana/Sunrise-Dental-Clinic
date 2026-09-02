package exception;

/**
 * Wraps a checked SQLException so the service and servlet layers are not
 * littered with JDBC plumbing. Becomes HTTP 500.
 */
public class DataAccessException extends AppException {
    public DataAccessException(String message, Throwable cause) {
        super(message, 500);
        initCause(cause);
    }
}
