package exception;

/** Raised when a signed-in user lacks the privilege. Becomes HTTP 403. */
public class ForbiddenException extends AppException {
    public ForbiddenException(String message) {
        super(message, 403);
    }
}
