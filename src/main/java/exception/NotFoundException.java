package exception;

/** Raised when a requested record does not exist. Becomes HTTP 404. */
public class NotFoundException extends AppException {
    public NotFoundException(String message) {
        super(message, 404);
    }
}
