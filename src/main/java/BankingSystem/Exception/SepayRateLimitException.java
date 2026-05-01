package BankingSystem.Exception;

public class SepayRateLimitException extends BankingException {
    public SepayRateLimitException() {
        super("SEPAY_RATE_LIMIT", "SePay API rate limit exceeded (3 req/s)");
    }
}
