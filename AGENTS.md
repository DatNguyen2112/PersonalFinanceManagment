---
name: sepay-personal-finance
description: >
  Hướng dẫn implement module Quản lý Tài chính Cá nhân (Personal Finance Management)
  tích hợp SePay Open API (Pull API + Webhook) + SePay Bank Hub trong hệ thống 
  Spring Boot Banking có sẵn (JWT, Kafka, MySQL, JPA). Hỗ trợ hai flow liên kết 
  ngân hàng: Bank Hub (hosted) + Manual Add. Bao gồm tự động phân loại chi tiêu,
  thống kê theo danh mục, báo cáo tài chính, ngân sách — theo đúng convention dự án
  (BankingDTO pattern, @Slf4j, @Transactional, MapStruct).

  Kích hoạt skill này khi người dùng đề cập đến: SePay, sepay api, tích hợp sepay,
  Bank Hub, liên kết ngân hàng, quản lý tài chính cá nhân, personal finance, budget,
  ngân sách, expense tracking, chi tiêu, financial report, báo cáo tài chính,
  spending category, danh mục chi tiêu, webhook giao dịch, đồng bộ giao dịch,
  hosted link.
---

# SePay Personal Finance — Banking System Extension

Module **Quản lý Tài chính Cá nhân qua SePay** hỗ trợ hai flow chính:

1. **Bank Hub (Recommended)** — User liên kết qua hosted link tại SePay (an toàn, OTP)
2. **Manual Add** — User nhập số tài khoản thủ công (fallback)

Cả hai flow đều kéo giao dịch qua **SePay API** (Pull) + **Webhook** (Push real-time),
tự động phân loại chi tiêu, báo cáo ngân sách.

---

## 1. Tổng quan kiến trúc

### 1.1 Hai Flow Liên kết Ngân hàng

```
                 ┌─────────────────────────────────────┐
                 │     1. Bank Hub Flow (Recommended)  │
                 │  User → Hosted Link → SePay Bank    │
                 │         Token → Callback → DB       │
                 └────────────┬────────────────────────┘
                              │
          ┌───────────────────┼────────────────────┐
          │                   │                    │
     SepayBankHubClient   SepayBankHubService   Webhook
     (init-link)         (processWebhookEvent)
          │                   │                    │
          ▼                   ▼                    ▼
    ┌──────────────────────────────────────────────────┐
    │    2. Manual Add Flow (Fallback)                │
    │  User → POST /bank-accounts → Direct Add → DB  │
    └──────────────────┬───────────────────────────────┘
                       │
        ┌──────────────┼────────────────────┐
        │              │                    │
   SepayBankAccountService  Pull API    Webhook Push
   (addAccount)        (Scheduled)     (Real-time)
        │              │                    │
        ▼              ▼                    ▼
 ┌──────────────────────────────────────────────────┐
 │         Spring Boot Application (DB)              │
 │                                                   │
 │  ┌─────────────────────────────────────────────┐ │
 │  │  SepayApiClient ──► SyncService             │ │
 │  │  SepayWebhookController ──► same            │ │
 │  │  SepayBankHubController ──► BankHubService  │ │
 │  │                         │                    │ │
 │  │  PersonalFinanceService                     │ │
 │  │   ├─ CategoryService (auto-classify)        │ │
 │  │   └─ BudgetService (track limits)           │ │
 │  │                         │                    │ │
 │  │         Kafka: banking.sepay.*               │ │
 │  └─────────────────────────────────────────────┘ │
 └──────────────────────────────────────────────────┘
```

**Ưu điểm:**
- **Bank Hub**: Hosted link (an toàn, OTP, user-friendly)
- **Manual Add**: Fallback nếu Bank Hub không khả dụng
- Rate limit: 3 req/s (Guava RateLimiter handle)

---

## 2. SePay API — Tham chiếu nhanh

### 2.1 Authentication

Mọi request dùng **Bearer Token** (API Token tạo tại `my.sepay.vn`):

```
Authorization: Apikey {YOUR_API_TOKEN}
```

### 2.2 Endpoints sử dụng

| Method | URL | Mô tả |
|--------|-----|-------|
| GET | `https://my.sepay.vn/userapi/transactions/list` | Lấy danh sách giao dịch |
| GET | `https://my.sepay.vn/userapi/transactions/details/{id}` | Chi tiết một giao dịch |
| GET | `https://my.sepay.vn/userapi/transactions/count` | Đếm tổng số giao dịch |

### 2.3 Query params — transactions/list

| Param | Ý nghĩa | Ví dụ |
|-------|---------|-------|
| `account_number` | Lọc theo số TK | `0071000888888` |
| `transaction_date_min` | Từ ngày (yyyy-MM-dd) | `2024-01-01` |
| `transaction_date_max` | Đến ngày (yyyy-MM-dd) | `2024-01-31` |
| `since_id` | Từ ID giao dịch (dùng cho incremental sync) | `49000` |
| `limit` | Số lượng tối đa (max 5000) | `100` |
| `amount_in` | Lọc tiền vào bằng đúng giá trị | `500000` |
| `amount_out` | Lọc tiền ra bằng đúng giá trị | — |
| `reference_number` | Lọc theo mã tham chiếu | — |

### 2.4 Response mẫu — transactions/list

```json
{
  "status": 200,
  "error": null,
  "messages": { "success": true },
  "transactions": [
    {
      "id": "49682",
      "bank_brand_name": "Vietcombank",
      "account_number": "0071000888888",
      "transaction_date": "2023-05-05 19:59:48",
      "amount_out": "0.00",
      "amount_in": "18067000.00",
      "accumulated": "1200541768.00",
      "transaction_content": "DUONG THUY ANH chuyen tien mua hang",
      "reference_number": "677760.050523.080001",
      "code": null,
      "sub_account": "VCB0011ABC004",
      "bank_account_id": "19"
    }
  ]
}
```

### 2.5 Webhook Payload (SePay → hệ thống)

SePay gọi POST đến endpoint của hệ thống mỗi khi có giao dịch mới:

```json
{
  "gateway": "Vietcombank",
  "transactionDate": "2024-01-15 10:30:00",
  "accountNumber": "0071000888888",
  "subAccount": "VCB0011ABC002",
  "amountIn": 500000,
  "amountOut": 0,
  "accumulated": 5500000,
  "code": "DH123456",
  "content": "NGUYEN VAN A chuyen khoan mua hang DH123456",
  "referenceCode": "731086.150124.103001",
  "description": "Vietcombank - 0071000888888 - +500,000"
}
```

---

## 3. Entities

### 3.1 SepayBankAccount — Tài khoản ngân hàng liên kết qua SePay

```java
@Entity
@Table(name = "sepay_bank_accounts",
    indexes = {
        @Index(name = "idx_sepay_user", columnList = "user_id"),
        @Index(name = "idx_sepay_account", columnList = "account_number", unique = true)
    })
@EntityListeners(AuditingEntityListener.class)
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SepayBankAccount {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "account_number", nullable = false, unique = true, length = 30)
    private String accountNumber;           // Số TK ngân hàng thực

    @Column(name = "bank_brand_name", length = 50)
    private String bankBrandName;           // "Vietcombank", "MB", "TCB" …

    @Column(name = "bank_account_id")
    private Long sepayBankAccountId;        // ID tài khoản trong SePay

    @Column(name = "display_name", length = 100)
    private String displayName;             // Tên hiển thị do user đặt

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "last_synced_transaction_id")
    private Long lastSyncedTransactionId;   // dùng for incremental pull

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SepayAccountStatus status = SepayAccountStatus.ACTIVE;

    @CreatedDate private LocalDateTime createdAt;
    @LastModifiedDate private LocalDateTime updatedAt;
}
```

### 3.2 SepayTransaction — Giao dịch kéo từ SePay

```java
@Entity
@Table(name = "sepay_transactions",
    indexes = {
        @Index(name = "idx_stx_user", columnList = "user_id"),
        @Index(name = "idx_stx_account", columnList = "account_number"),
        @Index(name = "idx_stx_date", columnList = "transaction_date"),
        @Index(name = "idx_stx_category", columnList = "category_id"),
        @Index(name = "idx_stx_sepay_id", columnList = "sepay_id", unique = true)
    })
@EntityListeners(AuditingEntityListener.class)
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SepayTransaction {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sepay_id", unique = true, nullable = false)
    private Long sepayId;                   // ID từ SePay — tránh duplicate

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sepay_bank_account_id")
    private SepayBankAccount sepayBankAccount;

    @Column(name = "account_number", nullable = false, length = 30)
    private String accountNumber;

    @Column(name = "bank_brand_name", length = 50)
    private String bankBrandName;

    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    @Column(name = "amount_in", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal amountIn = BigDecimal.ZERO;

    @Column(name = "amount_out", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal amountOut = BigDecimal.ZERO;

    @Column(nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal accumulated = BigDecimal.ZERO;  // Số dư sau GD

    @Column(name = "transaction_content", length = 1000)
    private String transactionContent;

    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    @Column(length = 100)
    private String code;                    // Mã đơn hàng (nếu có)

    @Column(name = "sub_account", length = 100)
    private String subAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private SpendingCategory category;      // Phân loại tự động

    @Column(name = "note", length = 500)
    private String note;                    // Ghi chú thêm của user

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TransactionDirection direction = TransactionDirection.IN; // IN | OUT

    @Column(name = "source")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SyncSource source = SyncSource.WEBHOOK;  // WEBHOOK | PULL_API

    @CreatedDate private LocalDateTime createdAt;
    @LastModifiedDate private LocalDateTime updatedAt;
}
```

### 3.3 SpendingCategory — Danh mục chi tiêu

```java
@Entity
@Table(name = "spending_categories",
    indexes = { @Index(name = "idx_cat_user", columnList = "user_id") })
@EntityListeners(AuditingEntityListener.class)
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SpendingCategory {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")           // null = danh mục hệ thống
    private User user;

    @Column(nullable = false, length = 100)
    private String name;                    // "Ăn uống", "Di chuyển", "Mua sắm"…

    @Column(name = "icon_code", length = 50)
    private String iconCode;                // emoji / icon key

    @Column(length = 7)
    private String color;                   // hex "#FF5733"

    @Column(name = "is_system")
    @Builder.Default
    private boolean system = false;         // true = Anthropic-seeded category

    @ElementCollection
    @CollectionTable(name = "category_keywords",
        joinColumns = @JoinColumn(name = "category_id"))
    @Column(name = "keyword", length = 100)
    private List<String> keywords = new ArrayList<>();  // keywords để auto-classify

    @CreatedDate private LocalDateTime createdAt;
}
```

### 3.4 MonthlyBudget — Ngân sách tháng

```java
@Entity
@Table(name = "monthly_budgets",
    indexes = {
        @Index(name = "idx_budget_user_month",
               columnList = "user_id, budget_year, budget_month", unique = false),
        @Index(name = "idx_budget_category",
               columnList = "user_id, category_id, budget_year, budget_month")
    })
@EntityListeners(AuditingEntityListener.class)
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MonthlyBudget {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")       // null = tổng ngân sách tháng
    private SpendingCategory category;

    @Column(name = "budget_year", nullable = false)
    private int year;

    @Column(name = "budget_month", nullable = false)
    private int month;                      // 1-12

    @Column(name = "limit_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal limitAmount;

    @Column(name = "alert_threshold")
    @Builder.Default
    private int alertThreshold = 80;        // % ngưỡng cảnh báo (default 80%)

    @CreatedDate private LocalDateTime createdAt;
    @LastModifiedDate private LocalDateTime updatedAt;
}
```

### 3.5 Enums

```java
public enum SepayAccountStatus { ACTIVE, PAUSED, REMOVED }
public enum TransactionDirection { IN, OUT }
public enum SyncSourceType { WEBHOOK, PULL_API }  // SyncSourceType (không phải SyncSource)
```

---

## 4. Configuration

### 4.1 application.yml

```yaml
sepay:
  api:
    base-url: https://my.sepay.vn/userapi
    token: ${SEPAY_API_TOKEN}              # API Token từ my.sepay.vn
    webhook-secret: ${SEPAY_WEBHOOK_SECRET}  # Verify webhook signature
    rate-limit-per-second: 3               # SePay giới hạn 3 req/s (Auto handle)
    sync:
      cron: "0 */15 * * * *"              # Pull mỗi 15 phút
      page-size: 100                      # Số GD mỗi lần pull

  # ── Bank Hub Configuration ──────────────────────────────────────────
  bank-hub:
    base-url: https://bankhub.sepay.vn
    client-id: ${SEPAY_BANK_HUB_CLIENT_ID}
    client-secret: ${SEPAY_BANK_HUB_CLIENT_SECRET}
    webhook-secret: ${SEPAY_BANK_HUB_WEBHOOK_SECRET}
    redirect-url: ${APP_BASE_URL}/api/v1/bank-hub/callback
    timeout-seconds: 300                  # Expiry of hosted link token
```

### 4.2 SepayProperties & SepayBankHubProperties

```java
@ConfigurationProperties(prefix = "sepay.api")
@Validated
public record SepayProperties(
    @NotBlank String baseUrl,
    @NotBlank String token,
    @NotBlank String webhookSecret,
    int rateLimitPerSecond,
    SyncConfig sync
) {
    public record SyncConfig(String cron, int pageSize) {}
}

@ConfigurationProperties(prefix = "sepay.bank-hub")
@Validated
public record SepayBankHubProperties(
    @NotBlank String baseUrl,
    @NotBlank String clientId,
    @NotBlank String clientSecret,
    @NotBlank String webhookSecret,
    @NotBlank String redirectUrl,
    int timeoutSeconds
) {}
```

### 4.3 SepayApiClient (RestClient + RateLimiter)

```java
@Component
@Slf4j
@RequiredArgsConstructor
public class SepayApiClient {

    private final SepayProperties props;
    private final RestClient restClient;
    private final RateLimiter rateLimiter;  // Guava RateLimiter (injected)

    // RateLimiter được cấu hình trong SepayConfig
    
    public SepayTransactionListResponse listTransactions(BankingDTO.SepayListRequest req) {
        rateLimiter.acquire();
        log.info("sepay_pull account={} since_id={}", req.accountNumber(), req.sinceId());
        // ... chi tiết tương tự như AGENTS.md gốc
    }

    public SepayTransactionDetailResponse getTransaction(Long sepayId) {
        rateLimiter.acquire();
        // ... chi tiết tương tự như AGENTS.md gốc
    }
}
```

### 4.4 SepayBankHubClient (Hosted Link Management)

```java
@Component
@Slf4j
@RequiredArgsConstructor
public class SepayBankHubClient {

    private final SepayBankHubProperties props;
    private final RestClient restClient;

    /**
     * Khởi tạo hosted link để user liên kết tài khoản ngân hàng.
     * Trả về hostedLinkUrl mà frontend sẽ navigate.
     */
    public SepayBankHub.BankHubInitResponse initLink(
            Long userId, String redirectUrl) {
        // POST to SePay Bank Hub
        // Request: { "userId": "...", "redirectUrl": "..." }
        // Response: { "hostedLinkUrl": "https://...", "linkTokenXid": "..." }
        log.info("bank_hub_init_link userId={}", userId);
        // ... returns hostedLinkUrl
    }

    /**
     * Khởi tạo hủy liên kết tài khoản.
     */
    public SepayBankHub.BankHubInitResponse initUnlink(
            String bankAccountXid) {
        // POST to SePay Bank Hub unlink endpoint
        log.info("bank_hub_init_unlink bankAccountXid={}", bankAccountXid);
        // ...
    }

    /**
     * Lấy danh sách tài khoản ngân hàng đã liên kết ở SePay Bank Hub
     * (dùng để sync về DB).
     */
    public List<SepayBankHub.LinkedBankAccount> getLinkedAccounts(String token) {
        // GET /linked-accounts with token
        log.info("bank_hub_get_linked_accounts");
        return restClient.get()
            .uri("/linked-accounts")
            .header("Authorization", "Bearer " + token)
            .retrieve()
            .body(new ParameterizedTypeReference<List<SepayBankHub.LinkedBankAccount>>() {});
    }
}
```

---

## 5. DTOs (thêm vào BankingDTO.java)

```java
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

// ── Bank Hub DTOs (NEW) ─────────────────────────────────────────────────────

public class SepayBankHub {
    public record BankHubInitResponse(
        String hostedLinkUrl,
        String linkTokenXid,
        long expiresAt
    ) {}

    public record BankHubUnlinkRequest(
        @NotBlank String bankAccountXid
    ) {}

    public record BankHubWebhookPayload(
        String event,                  // "account.linked", "account.unlinked", etc
        String linkTokenXid,
        String bankAccountXid,
        String accountNumber,
        String bankBrandName,
        String accountName,
        @JsonProperty("updatedAt") String updatedAt
    ) {}

    public record LinkedBankAccount(
        String bankAccountXid,
        String accountNumber,
        String bankBrandName,
        String accountName,
        String lastSyncAt
    ) {}

    public record BankHubBankAccountItem(
        String id,
        String name,
        String code,
        String logo
    ) {}
}

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
```

---

## 6. Services

### 6.1 SepayBankHubService — Hosted Link Management

```java
@Service @Slf4j @RequiredArgsConstructor
public class SepayBankHubService {

    private final SepayBankHubClient bankHubClient;
    private final SepayBankAccountService bankAccountService;
    private final SepayBankAccountRepository bankAccountRepository;
    private final SepayBankHubProperties props;
    private final KafkaProducerService kafkaProducerService;

    /**
     * Khởi tạo hosted link để user liên kết tài khoản ngân hàng.
     * Frontend sẽ navigate đến hostedLinkUrl.
     */
    @Transactional
    public SepayBankHub.BankHubInitResponse initLinkAccount(Long userId) {
        try {
            var redirectUrl = props.redirectUrl() + "?userId=" + userId;
            var response = bankHubClient.initLink(userId, redirectUrl);
            log.info("bank_hub_link_initiated userId={} linkTokenXid={}",
                userId, response.linkTokenXid());
            return response;
        } catch (Exception ex) {
            log.error("bank_hub_init_failed userId={}", userId, ex);
            throw new BankingException("BANK_HUB_INIT_ERROR",
                "Không thể khởi tạo liên kết tài khoản", ex);
        }
    }

    /**
     * Khởi tạo hủy liên kết tài khoản.
     */
    @Transactional
    public SepayBankHub.BankHubInitResponse initUnlinkAccount(
            Long userId, String bankAccountXid) {
        try {
            var response = bankHubClient.initUnlink(bankAccountXid);
            log.info("bank_hub_unlink_initiated userId={} xid={}", userId, bankAccountXid);
            return response;
        } catch (Exception ex) {
            log.error("bank_hub_unlink_init_failed userId={}", userId, ex);
            throw new BankingException("BANK_HUB_UNLINK_ERROR",
                "Không thể khởi tạo hủy liên kết", ex);
        }
    }

    /**
     * Xử lý webhook từ SePay Bank Hub khi user hoàn thành liên kết.
     * Event: "account.linked", "account.unlinked", etc.
     */
    @Transactional
    public void processWebhookEvent(SepayBankHub.BankHubWebhookPayload payload) {
        try {
            switch (payload.event()) {
                case "account.linked" -> syncNewLinkedAccount(payload);
                case "account.unlinked" -> removeLinkedAccount(payload);
                default -> log.info("bank_hub_webhook_unknown_event {}", payload.event());
            }
        } catch (Exception ex) {
            log.error("bank_hub_webhook_processing_failed event={}", payload.event(), ex);
        }
    }

    private void syncNewLinkedAccount(SepayBankHub.BankHubWebhookPayload payload) {
        // Lấy user từ token hoặc stored session — cần enhance
        // Tạm thời skip; trong thực tế sẽ cần lưu userIdFromSession trong cache khóa bởi linkTokenXid
        log.info("bank_hub_account_linked xid={} accountNumber={}",
            payload.bankAccountXid(), payload.accountNumber());

        // Tạo SepayAccount trong DB nếu chưa tồn tại
        // bankAccountService.addAccountFromBankHub(userId, payload);
    }

    private void removeLinkedAccount(SepayBankHub.BankHubWebhookPayload payload) {
        log.info("bank_hub_account_unlinked xid={}", payload.bankAccountXid());
        // Đánh dấu account là REMOVED trong DB
        bankAccountRepository.findByAccountNumber(payload.accountNumber())
            .ifPresent(acc -> {
                acc.setStatus(SepayAccountStatus.REMOVED);
                bankAccountRepository.save(acc);
            });
    }

    /**
     * Sync thủ công các tài khoản đã liên kết thành công qua Bank Hub.
     * Gọi khi user thoát khỏi hosted link hoặc manual trigger.
     */
    @Transactional
    public int syncLinkedAccountsFromBankHub(Long userId) {
        try {
            // Cần lấy token từ Bank Hub API
            // var token = bankHubClient.getAccessToken(userId);
            // var linkedAccounts = bankHubClient.getLinkedAccounts(token);
            
            // Tạm thời return 0
            log.info("bank_hub_manual_sync userId={}", userId);
            return 0;
        } catch (Exception ex) {
            log.error("bank_hub_sync_failed userId={}", userId, ex);
            return 0;
        }
    }
}
```

### 6.2 SepayTransactionSyncService — Pull API (Scheduled)

```java
@Service @Slf4j @RequiredArgsConstructor
public class SepayTransactionSyncService {

    private final SepayApiClient sepayApiClient;
    private final SepayBankAccountRepository bankAccountRepository;
    private final SepayTransactionRepository transactionRepository;
    private final SpendingCategoryService categoryService;
    private final KafkaProducerService kafkaProducerService;

    @Scheduled(cron = "${sepay.api.sync.cron}")
    public void scheduledSync() {
        log.info("sepay_scheduled_sync_start");
        bankAccountRepository.findByStatus(SepayAccountStatus.ACTIVE)
            .forEach(this::syncAccount);
        log.info("sepay_scheduled_sync_done");
    }

    @Transactional
    public void syncAccount(SepayAccount account) {
        try {
            var req = new BankingDTO.SepayListRequest(
                account.getAccountNumber(),
                account.getLastSyncedTransactionId(),  // incremental via since_id
                100,
                null, null
            );

            var response = sepayApiClient.listTransactions(req);
            if (response == null || response.transactions() == null
                    || response.transactions().isEmpty()) {
                return;
            }

            var newTxs = response.transactions().stream()
                .filter(item -> !transactionRepository.existsBySepayId(item.id()))
                .map(item -> mapToEntity(item, account))
                .toList();

            transactionRepository.saveAll(newTxs);

            // Cập nhật con trỏ incremental
            response.transactions().stream()
                .mapToLong(BankingDTO.SepayTransactionItem::id)
                .max()
                .ifPresent(maxId -> {
                    account.setLastSyncedTransactionId(maxId);
                    account.setLastSyncedAt(LocalDateTime.now());
                    bankAccountRepository.save(account);
                });

            log.info("sepay_sync_done account={} new_tx_count={}",
                account.getAccountNumber(), newTxs.size());

            // Kafka event
            if (!newTxs.isEmpty()) {
                kafkaProducerService.sendSepaySyncEvent(account.getUser().getId(),
                    account.getId(), newTxs.size());
            }

        } catch (Exception ex) {
            log.error("sepay_sync_failed account={}", account.getAccountNumber(), ex);
        }
    }

    private SepayTransaction mapToEntity(BankingDTO.SepayTransactionItem item,
                                         SepayAccount account) {
        var direction = item.amountIn() != null 
            && item.amountIn().compareTo(BigDecimal.ZERO) > 0
            ? TransactionDirection.IN
            : TransactionDirection.OUT;

        var category = categoryService.autoClassify(item.transactionContent());

        return SepayTransaction.builder()
            .sepayId(item.id())
            .user(account.getUser())
            .sepayBankAccount(account)
            .accountNumber(item.accountNumber())
            .bankBrandName(item.bankBrandName())
            .transactionDate(parseDate(item.transactionDate()))
            .amountIn(item.amountIn() != null ? item.amountIn() : BigDecimal.ZERO)
            .amountOut(item.amountOut() != null ? item.amountOut() : BigDecimal.ZERO)
            .accumulated(item.accumulated() != null ? item.accumulated() : BigDecimal.ZERO)
            .transactionContent(item.transactionContent())
            .referenceNumber(item.referenceNumber())
            .code(item.code())
            .subAccount(item.subAccount())
            .direction(direction)
            .category(category)
            .source(SyncSourceType.PULL_API)
            .build();
    }

    private LocalDateTime parseDate(String raw) {
        return LocalDateTime.parse(raw,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
```

### 6.3 SpendingCategoryService — Auto-classify

```java
@Service @Slf4j @RequiredArgsConstructor
public class SpendingCategoryService {

    private final SpendingCategoryRepository categoryRepository;

    /**
     * Phân loại giao dịch dựa trên keyword trong nội dung.
     * Trả về null nếu không khớp (để UI hiển thị "Khác").
     */
    public SpendingCategory autoClassify(String content) {
        if (content == null) return null;
        String lower = content.toLowerCase();

        return categoryRepository.findAll().stream()
            .filter(cat -> cat.getKeywords() != null && cat.getKeywords().stream()
                .anyMatch(kw -> lower.contains(kw.toLowerCase())))
            .findFirst()
            .orElse(null);
    }

    public List<SpendingCategory> getSystemCategories(Long userId) {
        // Trả về system categories + user-defined nếu có
        return categoryRepository.findBySystemOrUserId(true, userId);
    }
}
```

### 6.4 PersonalFinanceService — Budget & Reports

_(Chi tiết đầy đủ trong PersonalFinanceService.java — xem codebase hiện tại)_

---

## 7. Controllers

### 7.1 SepayWebhookController — Transaction Webhook

```java
@RestController
@RequestMapping("/api/v1/sepay")
@Slf4j
@RequiredArgsConstructor
public class SepayWebhookController {

    private final SepayWebhookService webhookService;
    private final SepayProperties props;

    /**
     * SePay gọi vào endpoint này khi có giao dịch mới.
     * Xác thực qua Apikey header.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> receiveWebhook(
            @RequestBody BankingDTO.SepayWebhookPayload payload,
            @RequestHeader(value = "Authorization", required = false) String auth) {

        if (!isValidAuth(auth)) {
            log.warn("sepay_webhook_unauthorized");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("success", false, "message", "Unauthorized"));
        }

        log.info("sepay_webhook_received account={} amountIn={} amountOut={}",
            payload.accountNumber(), payload.amountIn(), payload.amountOut());

        webhookService.process(payload);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private boolean isValidAuth(String auth) {
        return ("Apikey " + props.webhookSecret()).equals(auth);
    }
}
```

### 7.2 SepayBankHubController — Bank Hub Link Management

```java
@RestController
@RequestMapping("/api/v1/bank-hub")
@RequiredArgsConstructor
@Slf4j
public class SepayBankHubController {

    private final SepayBankHubService bankHubService;
    private final SepayBankHubProperties props;

    // ── Khởi tạo liên kết — frontend gọi để lấy hostedLinkUrl ────────────

    @PostMapping("/init-link")
    public ResponseEntity<SepayBankHub.BankHubInitResponse> initLink(
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails u) {
        return ResponseEntity.ok(bankHubService.initLinkAccount(u.getUserId()));
    }

    // ── Khởi tạo hủy liên kết ─────────────────────────────────────────────

    @PostMapping("/init-unlink")
    public ResponseEntity<SepayBankHub.BankHubInitResponse> initUnlink(
            @Valid @RequestBody SepayBankHub.BankHubUnlinkRequest req,
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails u) {
        return ResponseEntity.ok(
                bankHubService.initUnlinkAccount(u.getUserId(), req.bankAccountXid()));
    }

    // ── Callback — SePay redirect về sau khi user hoàn thành ──────────────

    @GetMapping("/callback")
    public ResponseEntity<Map<String, String>> callback(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String link_token_xid) {
        log.info("bank_hub_callback status={} linkTokenXid={}", status, link_token_xid);
        return ResponseEntity.ok(Map.of(
                "status", status != null ? status : "completed",
                "message", "Liên kết ngân hàng hoàn tất."));
    }

    // ── Webhook — SePay Bank Hub gọi vào khi có sự kiện liên kết ─────────

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> receiveWebhook(
            @RequestBody SepayBankHub.BankHubWebhookPayload payload,
            @RequestHeader(value = "Authorization", required = false) String auth) {

        if (!isValidAuth(auth)) {
            log.warn("bank_hub_webhook_unauthorized");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false));
        }

        bankHubService.processWebhookEvent(payload);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ── Sync thủ công ──────────────────────────────────────────────────────

    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> sync(
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails u) {
        int synced = bankHubService.syncLinkedAccountsFromBankHub(u.getUserId());
        return ResponseEntity.ok(Map.of("synced", synced));
    }

    private boolean isValidAuth(String auth) {
        return ("Apikey " + props.webhookSecret()).equals(auth);
    }
}
```

### 7.3 PersonalFinanceController — Transactions, Budget & Reports

```java
@RestController
@RequestMapping("/api/v1/personal-finance")
@RequiredArgsConstructor
@Slf4j
public class PersonalFinanceController {

    private final SepayBankAccountService bankAccountService;
    private final SepayTransactionSyncService syncService;
    private final PersonalFinanceService financeService;
    private final SpendingCategoryService categoryService;
    private final SepayBankHubService bankHubService;

    // ── Tài khoản ngân hàng ────────────────────────────────────────────────

    @PostMapping("/bank-accounts")
    public ResponseEntity<BankingDTO.BankAccountResponse> addAccount(
            @Valid @RequestBody BankingDTO.AddBankAccountRequest req,
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails u) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bankAccountService.addAccount(u.getUserId(), req));
    }

    @GetMapping("/bank-accounts")
    public ResponseEntity<List<BankingDTO.BankAccountResponse>> getAccounts(
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails u) {
        return ResponseEntity.ok(bankAccountService.getAccounts(u.getUserId()));
    }

    @PostMapping("/bank-accounts/{id}/sync")
    public ResponseEntity<Map<String, Object>> triggerSync(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails u) {
        var account = bankAccountService.getAccountForUser(id, u.getUserId());
        syncService.syncAccount(account);
        return ResponseEntity.ok(Map.of("message", "Sync triggered"));
    }

    // ── Giao dịch ──────────────────────────────────────────────────────────

    @GetMapping("/transactions")
    public ResponseEntity<Page<BankingDTO.TransactionResponse>> getTransactions(
            @Valid BankingDTO.TransactionQueryRequest req,
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails u) {
        return ResponseEntity.ok(
                financeService.getTransactions(u.getUserId(), req));
    }

    @PatchMapping("/transactions/{id}/category")
    public ResponseEntity<BankingDTO.TransactionResponse> updateCategory(
            @PathVariable Long id,
            @RequestParam Long categoryId,
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails u) {
        return ResponseEntity.ok(
                financeService.updateCategory(id, categoryId, u.getUserId()));
    }

    // ── Danh mục ───────────────────────────────────────────────────────────

    @GetMapping("/categories")
    public ResponseEntity<List<SpendingCategory>> getCategories(
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails u) {
        return ResponseEntity.ok(categoryService.getSystemCategories(u.getUserId()));
    }

    // ── Ngân sách ──────────────────────────────────────────────────────────

    @PostMapping("/budgets")
    public ResponseEntity<BankingDTO.BudgetStatusResponse> createBudget(
            @Valid @RequestBody BankingDTO.BudgetRequest req,
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails u) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(financeService.createOrUpdateBudget(u.getUserId(), req));
    }

    @GetMapping("/budgets")
    public ResponseEntity<List<BankingDTO.BudgetStatusResponse>> getBudgets(
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(defaultValue = "0") int month,
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails u) {
        int y = year  == 0 ? LocalDate.now().getYear()        : year;
        int m = month == 0 ? LocalDate.now().getMonthValue()  : month;
        return ResponseEntity.ok(financeService.getBudgets(u.getUserId(), y, m));
    }

    // ── Báo cáo ────────────────────────────────────────────────────────────

    @GetMapping("/reports/monthly")
    public ResponseEntity<BankingDTO.MonthlySummaryResponse> monthlySummary(
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(defaultValue = "0") int month,
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails u) {
        int y = year  == 0 ? LocalDate.now().getYear()       : year;
        int m = month == 0 ? LocalDate.now().getMonthValue() : month;
        return ResponseEntity.ok(financeService.getMonthlySummary(u.getUserId(), y, m));
    }
}
```

---

## 8. Repositories (JPQL chính)

```java
public interface SepayTransactionRepository
    extends JpaRepository<SepayTransaction, Long> {

    boolean existsBySepayId(Long sepayId);
    boolean existsByReferenceNumber(String referenceNumber);

    @Query("""
        SELECT COALESCE(SUM(t.amountOut), 0)
        FROM SepayTransaction t
        WHERE t.user.id = :userId
          AND t.transactionDate >= :start
          AND t.transactionDate < :end
        """)
    BigDecimal sumAmountOutByUserAndDateRange(
        @Param("userId") Long userId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);

    @Query("""
        SELECT COALESCE(SUM(t.amountOut), 0)
        FROM SepayTransaction t
        WHERE t.user.id = :userId
          AND t.category.id = :categoryId
          AND t.transactionDate >= :start
          AND t.transactionDate < :end
        """)
    BigDecimal sumAmountOutByUserAndCategoryAndDateRange(
        @Param("userId") Long userId,
        @Param("categoryId") Long categoryId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);

    @Query("""
        SELECT COALESCE(SUM(t.amountIn), 0)
        FROM SepayTransaction t
        WHERE t.user.id = :userId
          AND t.transactionDate >= :start
          AND t.transactionDate < :end
        """)
    BigDecimal sumAmountInByUserAndDateRange(
        @Param("userId") Long userId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);

    @Query("""
        SELECT c.name, c.color, SUM(t.amountOut), COUNT(t)
        FROM SepayTransaction t
        LEFT JOIN t.category c
        WHERE t.user.id = :userId
          AND t.amountOut > 0
          AND t.transactionDate >= :start
          AND t.transactionDate < :end
        GROUP BY c.name, c.color
        ORDER BY SUM(t.amountOut) DESC
        """)
    List<Object[]> sumOutByCategoryAndDateRange(
        @Param("userId") Long userId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);
}
```

---

## 9. Kafka Events & Topics

```java
public record SepayTransactionEvent(Long transactionId, String direction) {}
public record SepaySyncEvent(Long bankAccountId, int newTransactionCount) {}
public record BudgetAlertEvent(Long budgetId, int usagePercent) {}

// Producer service (KafkaProducerService.java)
@Service @Slf4j @RequiredArgsConstructor
public class KafkaProducerService {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    public void sendBudgetAlert(Long userId, BudgetAlertEvent event) {
        kafkaTemplate.send("banking.sepay.budget-alert", userId.toString(), event);
    }
    
    public void sendSepaySyncEvent(Long userId, Long bankAccountId, int count) {
        kafkaTemplate.send("banking.sepay.sync", userId.toString(),
            new SepaySyncEvent(bankAccountId, count));
    }
}
```

```properties
# application.properties
banking.kafka.topics.sepay-transaction=banking.sepay.transaction
banking.kafka.topics.sepay-sync=banking.sepay.sync
banking.kafka.topics.sepay-budget-alert=banking.sepay.budget-alert
```

---

## 10. SecurityConfig

```java
// @EnableWebSecurity
.requestMatchers("/api/v1/personal-finance/**").hasAnyRole("CUSTOMER", "ADMIN")
.requestMatchers("/api/v1/bank-hub/**").hasAnyRole("CUSTOMER", "ADMIN")
.requestMatchers("/api/v1/sepay/webhook").permitAll()      // SePay transaction webhook
.requestMatchers("/api/v1/bank-hub/webhook").permitAll()   // Bank Hub webhook
.requestMatchers("/api/v1/bank-hub/callback").permitAll()  // Hosted link callback
```

> **Lưu ý:** Webhook endpoints (`.permitAll()`) được bảo vệ bằng **Apikey header** trong Controller.

---

## 11. Data Seeder — System Categories

```java
@Component @RequiredArgsConstructor @Slf4j
public class SpendingCategorySeeder implements CommandLineRunner {

    private final SpendingCategoryRepository categoryRepository;

    private static final List<SeedData> SYSTEM_CATEGORIES = List.of(
        new SeedData("Ăn uống",      "🍜", "#FF6B6B",
            List.of("quan an", "nha hang", "cafe", "food", "pho", "com", "bun", "bia", "nuoc")),
        new SeedData("Di chuyển",    "🚗", "#4ECDC4",
            List.of("grab", "taxi", "xe", "xang", "dau", "parking", "bus", "vinbus")),
        new SeedData("Mua sắm",      "🛍️", "#45B7D1",
            List.of("shopee", "lazada", "tiki", "sendo", "mua hang", "sieu thi", "vinmart")),
        new SeedData("Hóa đơn",      "💡", "#96CEB4",
            List.of("dien", "nuoc", "internet", "dtv", "evn", "vnpt", "viettel", "fpt")),
        new SeedData("Giải trí",     "🎬", "#FFEAA7",
            List.of("cinema", "rap phim", "game", "netflix", "youtube premium", "spotify")),
        new SeedData("Sức khỏe",     "💊", "#DDA0DD",
            List.of("nha thuoc", "benh vien", "kham benh", "thuoc", "bvdc", "medic")),
        new SeedData("Giáo dục",     "📚", "#98D8C8",
            List.of("hoc phi", "sach", "khoa hoc", "udemy", "coursera", "truong")),
        new SeedData("Chuyển khoản", "💸", "#B0C4DE",
            List.of("chuyen khoan", "chuyen tien", "transfer"))
    );

    @Override
    public void run(String... args) {
        if (categoryRepository.countBySystemTrue() > 0) return;

        SYSTEM_CATEGORIES.forEach(data -> categoryRepository.save(
            SpendingCategory.builder()
                .name(data.name()).iconCode(data.icon()).color(data.color())
                .system(true).keywords(data.keywords())
                .build()
        ));

        log.info("spending_categories_seeded count={}", SYSTEM_CATEGORIES.size());
    }

    private record SeedData(
        String name, String icon, String color, List<String> keywords) {}
}
```

---

## 12. Custom Exceptions

```java
public class SepayApiException extends RuntimeException {
    public SepayApiException(String message) { super(message); }
    public SepayApiException(String message, Throwable cause) { super(message, cause); }
}

public class BankingException extends RuntimeException {
    private final String code;
    public BankingException(String code, String message) {
        super(message);
        this.code = code;
    }
    public BankingException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}

public class BankAccountNotFoundException extends BankingException {
    public BankAccountNotFoundException(String details) {
        super("ACCOUNT_NOT_FOUND", "Bank account not found: " + details);
    }
}

public class BudgetNotFoundException extends BankingException {
    public BudgetNotFoundException(Long categoryId, int year, int month) {
        super("BUDGET_NOT_FOUND",
            "Budget not found for categoryId=%s year=%d month=%d"
                .formatted(categoryId, year, month));
    }
}
```

---

## 14. Checklist triển khai

- [ ] Thêm env vars: `SEPAY_API_TOKEN`, `SEPAY_WEBHOOK_SECRET`, `SEPAY_BANK_HUB_CLIENT_ID`, `SEPAY_BANK_HUB_CLIENT_SECRET`, `SEPAY_BANK_HUB_WEBHOOK_SECRET`, `APP_BASE_URL`
- [ ] Thêm @EnableScheduling vào `BankingSystemApplication.java`
- [ ] Kiểm tra Config/Sepay: `SepayConfig.java` cấu hình RestClient + RateLimiter
- [ ] Kiểm tra Entity: `SepayAccount`, `SepayTransaction`, `SpendingCategory`, `Budget`
- [ ] Kiểm tra Enum: `SepayAccountStatus`, `TransactionDirection`, `SyncSourceType`
- [ ] Kiểm tra Controller: `PersonalFinanceController`, `SepayBankHubController`, `SepayWebhookController`
- [ ] Kiểm tra Service: `PersonalFinanceService`, `SepayBankAccountService`, `SepayTransactionSyncService`, `SepayBankHubService`, `SpendingCategoryService`
- [ ] Kiểm tra Repository: `SepayTransactionRepository`, `SepayBankAccountRepository`, `BudgetRepository`, `SpendingCategoryRepository`
- [ ] Cập nhật `SecurityConfig`: thêm `/api/v1/bank-hub/**`, `/api/v1/sepay/webhook`, `/api/v1/bank-hub/webhook`, `/api/v1/bank-hub/callback` với `.permitAll()`
- [ ] Thêm Kafka topics vào `application.properties`
- [ ] Chạy `mvn clean install` và `mvn test`
- [ ] Đăng ký Webhook URLs tại `my.sepay.vn`:
  - Webhook Transaction: `/api/v1/sepay/webhook`
  - Bank Hub Webhook: `/api/v1/bank-hub/webhook`
  - Bank Hub Callback: `/api/v1/bank-hub/callback`
- [ ] Test Swagger UI: `/swagger-ui.html` → verify "Personal Finance" tags

---

## 15. Lưu ý quan trọng

> **Bank Hub vs Manual Add:**
> - **Recommended**: Bank Hub (hosted link — user-friendly, OTP-protected)
> - **Fallback**: Manual Add (user nhập number — nếu Bank Hub không khả dụng)
> - Webhook từ Bank Hub khác webhook giao dịch — cần xử lý riêng

> **Pull API + Incremental Sync:**
> - `since_id` tránh kéo lại toàn bộ lịch sử
> - Scheduled mỗi 15 phút (tunable)
> - Rate limit: 3 req/s (`RateLimiter.acquire()` auto-block)

> **Webhook Deduplication:**
> - Transaction webhook: dùng `referenceCode`
> - Bank Hub webhook: dùng `linkTokenXid` hoặc `bankAccountXid`
> - Implement idempotency key trong DB check

> **Kafka Events:**
> - `budget-alert`: Trigger cảnh báo khi exceed threshold
> - `sepay-sync`: Notify sync completion (count transactions)
> - `sepay-transaction`: Real-time transaction event

> **Security:**
> - Mask account numbers in logs
> - Never log API token — use `****` masking
> - Verify Apikey header in webhook endpoints
> - Bank Hub redirect URL phải HTTPS

> **Future Enhancements:**
> - Savings goals (target amount + deadline)
> - Recurring transactions (auto-classify, template)
> - Budget forecast (trend analysis)
> - Export reports (PDF, CSV)
> - Multi-currency support (VND, USD, etc)
