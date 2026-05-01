package BankingSystem.Exception;

public class EmailAlreadyExistsException extends AuthException {
    public EmailAlreadyExistsException(String email) {
        super("EMAIL_ALREADY_EXISTS", "Email đã được sử dụng: " + email);
    }
}
