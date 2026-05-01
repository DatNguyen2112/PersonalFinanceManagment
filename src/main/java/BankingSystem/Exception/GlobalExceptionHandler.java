package BankingSystem.Exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.security.auth.login.AccountLockedException;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ── Banking domain exceptions ──────────────────────────────────────────

    @ExceptionHandler(BankingException.class)
    public ResponseEntity<ErrorResponse> handleBankingException(
            BankingException ex, HttpServletRequest request) {
        log.warn("banking_exception code={} message={} path={}",
                ex.getErrorCode(), ex.getMessage(), request.getRequestURI());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(SepayApiException.class)
    public ResponseEntity<ErrorResponse> handleSepayApiException(
            SepayApiException ex, HttpServletRequest request) {
        log.error("sepay_api_exception message={} path={}",
                ex.getMessage(), request.getRequestURI(), ex);
        return buildResponse(HttpStatus.BAD_GATEWAY,
                ex.getErrorCode(), "SePay API không khả dụng, vui lòng thử lại sau");
    }

    @ExceptionHandler(SepayRateLimitException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(
            SepayRateLimitException ex, HttpServletRequest request) {
        log.warn("sepay_rate_limit path={}", request.getRequestURI());
        return buildResponse(HttpStatus.TOO_MANY_REQUESTS,
                ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(BankAccountAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleAccountExists(
            BankAccountAlreadyExistsException ex, HttpServletRequest request) {
        log.warn("bank_account_exists message={}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler({
            BudgetNotFoundException.class,
            BankAccountNotFoundException.class,
            EntityNotFoundException.class
    })
    public ResponseEntity<ErrorResponse> handleNotFound(
            RuntimeException ex, HttpServletRequest request) {
        log.warn("not_found message={} path={}", ex.getMessage(), request.getRequestURI());
        return buildResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    // ── Validation ─────────────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex) {
        var errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid",
                        (a, b) -> a));

        log.warn("validation_failed errors={}", errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                new ErrorResponse("VALIDATION_ERROR", "Dữ liệu không hợp lệ", errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex) {
        var errors = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        cv -> cv.getPropertyPath().toString(),
                        cv -> cv.getMessage(),
                        (a, b) -> a));

        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Dữ liệu không hợp lệ");
    }

    // ── Auth ───────────────────────────────────────────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        log.warn("access_denied path={}", request.getRequestURI());
        return buildResponse(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "Bạn không có quyền thực hiện thao tác này");
    }

    // ── Auth exceptions ────────────────────────────────────────────────────────

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException ex) {
        log.warn("auth_invalid_credentials");
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler({TokenExpiredException.class, RefreshTokenExpiredException.class})
    public ResponseEntity<ErrorResponse> handleTokenExpired(AuthException ex) {
        log.warn("auth_token_expired code={}", ex.getErrorCode());
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToken(InvalidTokenException ex) {
        log.warn("auth_invalid_token");
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler({EmailAlreadyExistsException.class,
            UsernameAlreadyExistsException.class})
    public ResponseEntity<ErrorResponse> handleAlreadyExists(AuthException ex) {
        log.warn("auth_already_exists code={}", ex.getErrorCode());
        return buildResponse(HttpStatus.CONFLICT, ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex) {
        log.warn("auth_unauthorized message={}", ex.getMessage());
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getErrorCode(), ex.getMessage());
    }

    // ── Fallback ───────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(
            Exception ex, HttpServletRequest request) {
        log.error("unhandled_exception path={} error={}",
                request.getRequestURI(), ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR", "Đã xảy ra lỗi, vui lòng thử lại sau");
    }

    // ── Helper ─────────────────────────────────────────────────────────────

    private ResponseEntity<ErrorResponse> buildResponse(
            HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(code, message, null));
    }

    // ── Error Response ─────────────────────────────────────────────────────

    public record ErrorResponse(
            String code,
            String message,
            Map<String, String> errors
    ) {
        public ErrorResponse(String code, String message) {
            this(code, message, null);
        }
    }


}
