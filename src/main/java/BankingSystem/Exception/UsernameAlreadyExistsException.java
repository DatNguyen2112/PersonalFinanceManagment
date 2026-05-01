package BankingSystem.Exception;

public class UsernameAlreadyExistsException extends AuthException {
    public UsernameAlreadyExistsException(String username) {
        super("USERNAME_ALREADY_EXISTS", "Username đã được sử dụng: " + username);
    }
}
