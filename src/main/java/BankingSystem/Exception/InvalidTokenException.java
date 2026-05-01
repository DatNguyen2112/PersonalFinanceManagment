package BankingSystem.Exception;

public class InvalidTokenException extends AuthException {
    public InvalidTokenException(String message) {
        super("INVALID_TOKEN", message);
    }
}
