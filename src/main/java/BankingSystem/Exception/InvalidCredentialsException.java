package BankingSystem.Exception;

public class InvalidCredentialsException extends AuthException {
    public InvalidCredentialsException() {
        super("INVALID_CREDENTIALS", "Email hoặc mật khẩu không đúng");
    }
}
