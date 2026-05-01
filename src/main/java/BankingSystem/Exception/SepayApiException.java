package BankingSystem.Exception;

// ── Domain Exceptions ──────────────────────────────────────────────────────
public class SepayApiException extends BankingException {
    public SepayApiException(String message) {
        super("SEPAY_API_ERROR", message);
    }
    public SepayApiException(String message, Throwable cause) {
        super("SEPAY_API_ERROR", message, cause);
    }
}