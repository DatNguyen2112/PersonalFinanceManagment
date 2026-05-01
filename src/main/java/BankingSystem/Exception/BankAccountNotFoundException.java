package BankingSystem.Exception;

public class BankAccountNotFoundException extends BankingException {
    public BankAccountNotFoundException(String identifier) {
        super("BANK_ACCOUNT_NOT_FOUND",
                "Bank account not found: " + identifier);
    }
}
