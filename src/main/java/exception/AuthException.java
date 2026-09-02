package exception;

/** Raised for bad credentials or a missing session. Becomes HTTP 401. */
public class AuthException extends AppException {
    public AuthException(String message) {
        super(message, 401);
    }
}
