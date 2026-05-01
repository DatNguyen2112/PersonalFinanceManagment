package BankingSystem.Exception;

public class BankAccountAlreadyExistsException extends BankingException {
    public BankAccountAlreadyExistsException(String accountNumber) {
        super("BANK_ACCOUNT_ALREADY_EXISTS",
                "Account already registered: " + accountNumber);
    }
}
