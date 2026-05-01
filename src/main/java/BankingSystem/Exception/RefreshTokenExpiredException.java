package BankingSystem.Exception;

public class RefreshTokenExpiredException extends AuthException {
    public RefreshTokenExpiredException() {
        super("REFRESH_TOKEN_EXPIRED",
                "Refresh token đã hết hạn, vui lòng đăng nhập lại");
    }
}
