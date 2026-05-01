package BankingSystem.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public class SepayBankHub {

    // ── Bank Hub Request/Response ──────────────────────────────────────────────

    public record BankHubInitRequest(
            String displayName   // tên hiển thị của user (optional)
    ) {}

    public record BankHubInitResponse(
            String linkTokenXid,
            String hostedLinkUrl,
            String expiresAt
    ) {}

    public record BankHubUnlinkRequest(
            @NotBlank String bankAccountXid   // xid của tài khoản cần hủy liên kết
    ) {}

// ── Bank Hub Webhook payload ───────────────────────────────────────────────

    public record BankHubWebhookPayload(
            Long timestamp,
            String xid,
            String version,
            String event,
            BankHubWebhookMetadata metadata
    ) {}

    public record BankHubWebhookMetadata(
            // LINK_TOKEN_CREATED
            String purpose,
            @JsonProperty("link_token_xid")  String linkTokenXid,
            @JsonProperty("link_session_xid") String linkSessionXid,

            // LINK_SESSION_STATE_CHANGED
            String state,
            @JsonProperty("brand_name")          String brandName,
            @JsonProperty("account_type")        String accountType,
            @JsonProperty("account_number")      String accountNumber,
            @JsonProperty("account_holder_name") String accountHolderName,

            // BANK_ACCOUNT_LINKED / BANK_ACCOUNT_UNLINKED
            @JsonProperty("bank_account_xid")    String bankAccountXid
    ) {}

// ── Bank Hub Access Token ──────────────────────────────────────────────────

    public record BankHubTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in")   int expiresIn,
            @JsonProperty("token_type")   String tokenType
    ) {}

// ── Bank Hub Link Token API response ──────────────────────────────────────

    public record BankHubLinkTokenResponse(
            String xid,
            @JsonProperty("hosted_link_url") String hostedLinkUrl,
            @JsonProperty("link_token")      String linkToken,
            @JsonProperty("expires_at")      String expiresAt
    ) {}

// ── Bank Hub Bank Account (từ SePay) ──────────────────────────────────────

    public record BankHubBankAccountItem(
            String xid,
            @JsonProperty("company_xid")          String companyXid,
            @JsonProperty("brand_name")           String brandName,
            @JsonProperty("account_holder_name")  String accountHolderName,
            @JsonProperty("account_number")       String accountNumber,
            String accumulated,
            @JsonProperty("account_type")         String accountType,
            @JsonProperty("bank_api_connected")   String bankApiConnected,
            String active,
            @JsonProperty("last_transaction")     String lastTransaction,
            @JsonProperty("created_at")           String createdAt,
            @JsonProperty("updated_at")           String updatedAt
    ) {}
}
