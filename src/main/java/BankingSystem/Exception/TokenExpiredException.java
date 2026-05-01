package BankingSystem.Exception;

public class TokenExpiredException extends AuthException {
    public TokenExpiredException() {
        super("TOKEN_EXPIRED", "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại");
    }
}
