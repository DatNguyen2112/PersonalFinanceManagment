package BankingSystem.Exception;

public class UnauthorizedException extends AuthException {
    public UnauthorizedException() {
        super("UNAUTHORIZED", "Bạn chưa đăng nhập");
    }
    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", message);
    }
}
