package exception;

/** Raised when user input fails a validation rule. Becomes HTTP 400. */
public class ValidationException extends AppException {
    public ValidationException(String message) {
        super(message, 400);
    }
}
