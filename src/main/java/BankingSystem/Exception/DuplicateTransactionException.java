package BankingSystem.Exception;

public class DuplicateTransactionException extends BankingException {
    public DuplicateTransactionException(String ref) {
        super("DUPLICATE_TRANSACTION",
                "Transaction already exists ref=" + ref);
    }
}
