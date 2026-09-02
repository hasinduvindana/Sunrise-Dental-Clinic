package exception;

/** Raised on duplicates and business-rule clashes. Becomes HTTP 409. */
public class ConflictException extends AppException {
    public ConflictException(String message) {
        super(message, 409);
    }
}
