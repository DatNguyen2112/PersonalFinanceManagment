package BankingSystem.Exception;

public class BankHubException extends BankingException {
    public BankHubException(String message) {
        super("BANK_HUB_ERROR", message);
    }
    public BankHubException(String message, Throwable cause) {
        super("BANK_HUB_ERROR", message, cause);
    }
}
