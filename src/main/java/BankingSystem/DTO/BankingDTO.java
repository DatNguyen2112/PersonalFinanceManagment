package BankingSystem.DTO;

import BankingSystem.Entity.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class BankingDTO {
    // ========================
    // Auth DTOs
    // ========================
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisterRequest {
        @NotBlank
        @Size(min = 3, max = 50)
        private String username;
        @NotBlank
        @Email
        private String email;
        @NotBlank
        @Size(min = 8)
        private String password;
        @NotBlank
        @Size(min = 2, max = 50)
        private String firstName;
        @NotBlank
        @Size(min = 2, max = 50)
        private String lastName;
        @Size(max = 20)
        private String phoneNumber;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        @NotBlank
        private String username;
        @NotBlank
        private String password;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthResponse {
        private String accessToken;
        private String refreshToken;
        private String tokenType;
        private Long expiresIn;
        private UserDTO user;
    }

    // ========================
    // User DTOs
    // ========================
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserDTO {
        private Long id;
        private String username;
        private String email;
        private String firstName;
        private String lastName;
        private String phoneNumber;
        private User.UserRole role;
        private Boolean enabled;
        private LocalDateTime createdAt;
        private LocalDateTime lastLogin;
    }

    // ── SePay API response DTOs ────────────────────────────────────────────────

    public record SepayTransactionItem(
            @JsonProperty("id") Long id,
            @JsonProperty("bank_brand_name") String bankBrandName,
            @JsonProperty("account_number") String accountNumber,
            @JsonProperty("transaction_date") String transactionDate,
            @JsonProperty("amount_in") BigDecimal amountIn,
            @JsonProperty("amount_out") BigDecimal amountOut,
            @JsonProperty("accumulated") BigDecimal accumulated,
            @JsonProperty("transaction_content") String transactionContent,
            @JsonProperty("reference_number") String referenceNumber,
            @JsonProperty("code") String code,
            @JsonProperty("sub_account") String subAccount,
            @JsonProperty("bank_account_id") Long bankAccountId
    ) {}

    public record SepayTransactionListResponse(
            int status,
            String error,
            List<SepayTransactionItem> transactions
    ) {}

    public record SepayTransactionDetailResponse(
            int status,
            String error,
            SepayTransactionItem transaction
    ) {}

    public record SepayListRequest(
            String accountNumber,
            Long sinceId,
            int limit,
            LocalDate dateMin,
            LocalDate dateMax
    ) {}

    // ── Webhook payload ────────────────────────────────────────────────────────

    public record SepayWebhookPayload(
            String gateway,
            @JsonProperty("transactionDate") String transactionDate,
            @JsonProperty("accountNumber") String accountNumber,
            @JsonProperty("subAccount") String subAccount,
            @JsonProperty("amountIn") BigDecimal amountIn,
            @JsonProperty("amountOut") BigDecimal amountOut,
            BigDecimal accumulated,
            String code,
            String content,
            @JsonProperty("referenceCode") String referenceCode,
            String description
    ) {}

    // ── Application-level DTOs ─────────────────────────────────────────────────

    public record AddBankAccountRequest(
            @NotBlank String accountNumber,
            @NotBlank String bankBrandName,
            String displayName
    ) {}

    public record BankAccountResponse(
            Long id,
            String accountNumber,
            String bankBrandName,
            String displayName,
            LocalDateTime lastSyncedAt,
            String status
    ) {}

    public record TransactionResponse(
            Long id,
            Long sepayId,
            String accountNumber,
            String bankBrandName,
            LocalDateTime transactionDate,
            BigDecimal amountIn,
            BigDecimal amountOut,
            BigDecimal accumulated,
            String transactionContent,
            String referenceNumber,
            String code,
            String categoryName,
            String note,
            String direction,
            String source
    ) {}

    public record TransactionQueryRequest(
            String accountNumber,
            LocalDate dateMin,
            LocalDate dateMax,
            Long categoryId,
            String direction,          // IN | OUT
            int page,
            int size
    ) {}

    public record BudgetRequest(
            Long categoryId,
            int year,
            int month,
            @NotNull @Positive BigDecimal limitAmount,
            @Min(1) @Max(100) int alertThreshold
    ) {}

    public record BudgetStatusResponse(
            Long budgetId,
            String categoryName,
            int year,
            int month,
            BigDecimal limitAmount,
            BigDecimal spentAmount,
            BigDecimal remainingAmount,
            int usagePercent,
            boolean alertTriggered
    ) {}

    public record MonthlySummaryResponse(
            int year,
            int month,
            BigDecimal totalIn,
            BigDecimal totalOut,
            BigDecimal netBalance,
            List<CategoryBreakdown> breakdown
    ) {
        public record CategoryBreakdown(
                String categoryName,
                String color,
                BigDecimal amount,
                int transactionCount,
                int sharePercent
        ) {}
    }
}
