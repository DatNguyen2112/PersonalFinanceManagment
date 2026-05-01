package BankingSystem.Exception;

// ── Base Auth Exception ────────────────────────────────────────────────────
public class AuthException extends BankingException {
    public AuthException(String errorCode, String message) {
        super(errorCode, message);
    }
    public AuthException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
