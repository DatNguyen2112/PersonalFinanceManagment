---
name: sepay-personal-finance
description: >
  Hướng dẫn implement module Quản lý Tài chính Cá nhân (Personal Finance Management)
  tích hợp SePay Open API trong hệ thống Spring Boot Banking có sẵn (JWT, Kafka,
  MySQL, JPA). Thay thế mô hình liên kết trực tiếp ngân hàng bằng cách kéo giao dịch
  qua SePay — bao gồm Pull API (polling) + Webhook nhận giao dịch real-time, tự động
  phân loại chi tiêu, thống kê theo danh mục, báo cáo tài chính — theo đúng convention
  dự án (BankingDTO pattern, @Slf4j, Testcontainers, v.v.).

  Kích hoạt skill này khi người dùng đề cập đến: SePay, sepay api, tích hợp sepay,
  quản lý tài chính cá nhân, personal finance, budget, ngân sách, expense tracking,
  chi tiêu, savings goal, mục tiêu tiết kiệm, financial report, báo cáo tài chính,
  spending category, danh mục chi tiêu, webhook giao dịch, biến động số dư, đồng bộ
  giao dịch ngân hàng — trong ngữ cảnh dự án banking Spring Boot này.
---

# SePay Personal Finance — Banking System Extension

Module **Quản lý Tài chính Cá nhân qua SePay Open API** — không yêu cầu user liên kết
trực tiếp với ngân hàng. Thay vào đó, user liên kết tài khoản ngân hàng vào SePay
(qua OTP, an toàn), rồi hệ thống nhận giao dịch qua **SePay API** (pull) và
**SePay Webhook** (push real-time).

---

## 1. Tổng quan kiến trúc

```
                    ┌──────────────────────────────────────────┐
                    │              SePay Platform               │
                    │  (User liên kết ngân hàng tại SePay)     │
                    └────────────┬─────────────────────────────┘
                                 │
              ┌──────────────────┼──────────────────┐
              │                  │                  │
    REST Pull API          Webhook Push         SePay Dashboard
  (Scheduled Sync)      (Real-time notify)    (my.sepay.vn)
              │                  │
              ▼                  ▼
   ┌──────────────────────────────────────┐
   │          Spring Boot Application     │
   │                                      │
   │  SepayApiClient ──► SyncService      │
   │  SepayWebhookController ──► same     │
   │                      │               │
   │              TransactionService      │
   │                      │               │
   │         ┌────────────┼────────────┐  │
   │         ▼            ▼            ▼  │
   │   CategoryService  BudgetService  │  │
   │   (auto-classify) (track limits)  │  │
   │                      │            │  │
   │              Kafka: banking.sepay  │  │
   └──────────────────────────────────────┘
```

**Ưu điểm so với liên kết trực tiếp ngân hàng:**
- Không cần xử lý OAuth2 từng ngân hàng riêng lẻ
- SePay đã hợp tác chính thức với ngân hàng (không bot/scraping)
- Hỗ trợ hầu hết ngân hàng Việt Nam qua một đầu mối duy nhất
- Rate limit SePay: 3 request/giây — cần throttle khi polling

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
public enum SyncSource { WEBHOOK, PULL_API }
```

---

## 4. Configuration

### 4.1 application.yml

```yaml
sepay:
  api:
    base-url: https://my.sepay.vn/userapi
    token: ${SEPAY_API_TOKEN}       # API Token từ my.sepay.vn
    webhook-secret: ${SEPAY_WEBHOOK_SECRET}  # dùng để verify webhook
    rate-limit-per-second: 3        # SePay giới hạn 3 req/s
    sync:
      cron: "0 */15 * * * *"        # Pull mỗi 15 phút
      page-size: 100                # số GD mỗi lần pull
```

### 4.2 SepayProperties

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
```

### 4.3 SepayApiClient (RestClient + rate limiter)

```java
@Component
@Slf4j
@RequiredArgsConstructor
public class SepayApiClient {

    private final SepayProperties props;
    private final RestClient restClient;
    private final RateLimiter rateLimiter;  // Guava RateLimiter

    @Bean
    static RateLimiter sepayRateLimiter(SepayProperties props) {
        return RateLimiter.create(props.rateLimitPerSecond());
    }

    public SepayTransactionListResponse listTransactions(SepayListRequest req) {
        rateLimiter.acquire();
        log.info("sepay_pull account={} since_id={}", req.accountNumber(), req.sinceId());
        return restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/transactions/list")
                .queryParamIfPresent("account_number", Optional.ofNullable(req.accountNumber()))
                .queryParamIfPresent("since_id", Optional.ofNullable(req.sinceId()))
                .queryParamIfPresent("limit", Optional.of(req.limit()))
                .queryParamIfPresent("transaction_date_min",
                    Optional.ofNullable(req.dateMin()).map(Object::toString))
                .queryParamIfPresent("transaction_date_max",
                    Optional.ofNullable(req.dateMax()).map(Object::toString))
                .build())
            .header("Authorization", "Apikey " + props.token())
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                throw new SepayApiException("SePay 4xx: " + response.getStatusCode());
            })
            .body(SepayTransactionListResponse.class);
    }

    public SepayTransactionDetailResponse getTransaction(Long sepayId) {
        rateLimiter.acquire();
        return restClient.get()
            .uri("/transactions/details/{id}", sepayId)
            .header("Authorization", "Apikey " + props.token())
            .retrieve()
            .body(SepayTransactionDetailResponse.class);
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

### 6.1 SepayTransactionSyncService — Pull API

```java
@Service @Slf4j @RequiredArgsConstructor
public class SepayTransactionSyncService {

    private final SepayApiClient sepayApiClient;
    private final SepayBankAccountRepository bankAccountRepository;
    private final SepayTransactionRepository transactionRepository;
    private final SpendingCategoryService categoryService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(cron = "${sepay.api.sync.cron}")
    public void scheduledSync() {
        log.info("sepay_scheduled_sync_start");
        bankAccountRepository.findByStatus(SepayAccountStatus.ACTIVE)
            .forEach(this::syncAccount);
        log.info("sepay_scheduled_sync_done");
    }

    public void syncAccount(SepayBankAccount account) {
        try {
            var req = new SepayListRequest(
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
                .mapToLong(SepayTransactionItem::id)
                .max()
                .ifPresent(maxId -> {
                    account.setLastSyncedTransactionId(maxId);
                    account.setLastSyncedAt(LocalDateTime.now());
                    bankAccountRepository.save(account);
                });

            log.info("sepay_sync_done account={} new_tx_count={}",
                account.getAccountNumber(), newTxs.size());

            // Kafka event cho downstream (notification, budget check…)
            if (!newTxs.isEmpty()) {
                kafkaTemplate.send("banking.sepay.sync",
                    account.getUser().getId().toString(),
                    new SepaySyncEvent(account.getId(), newTxs.size()));
            }

        } catch (SepayApiException ex) {
            log.error("sepay_sync_failed account={}", account.getAccountNumber(), ex);
        }
    }

    private SepayTransaction mapToEntity(SepayTransactionItem item,
                                         SepayBankAccount account) {
        var direction = item.amountIn().compareTo(BigDecimal.ZERO) > 0
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
            .amountIn(item.amountIn())
            .amountOut(item.amountOut())
            .accumulated(item.accumulated())
            .transactionContent(item.transactionContent())
            .referenceNumber(item.referenceNumber())
            .code(item.code())
            .subAccount(item.subAccount())
            .direction(direction)
            .category(category)
            .source(SyncSource.PULL_API)
            .build();
    }

    private LocalDateTime parseDate(String raw) {
        return LocalDateTime.parse(raw,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
```

### 6.2 SpendingCategoryService — Auto-classify

```java
@Service @Slf4j @RequiredArgsConstructor
public class SpendingCategoryService {

    private final SpendingCategoryRepository categoryRepository;

    /**
     * Phân loại giao dịch dựa trên keyword trong nội dung.
     * Priority: user-defined > system categories.
     * Trả về null nếu không khớp (để UI hiển thị "Khác").
     */
    public SpendingCategory autoClassify(String content) {
        if (content == null) return null;
        String lower = content.toLowerCase();

        return categoryRepository.findAllWithKeywords().stream()
            .filter(cat -> cat.getKeywords().stream()
                .anyMatch(kw -> lower.contains(kw.toLowerCase())))
            .findFirst()
            .orElse(null);
    }

    public List<SpendingCategory> getSystemCategories() {
        return categoryRepository.findBySystemTrue();
    }
}
```

### 6.3 PersonalFinanceService — Budget & Reports

```java
@Service @Slf4j @RequiredArgsConstructor
public class PersonalFinanceService {

    private final SepayTransactionRepository transactionRepository;
    private final MonthlyBudgetRepository budgetRepository;
    private final SpendingCategoryRepository categoryRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // ── Budget ─────────────────────────────────────────────────────────────

    public BankingDTO.BudgetStatusResponse createOrUpdateBudget(
            Long userId, BankingDTO.BudgetRequest req) {

        var category = req.categoryId() != null
            ? categoryRepository.findById(req.categoryId()).orElseThrow(
                () -> new EntityNotFoundException("Category not found"))
            : null;

        var budget = budgetRepository
            .findByUserIdAndCategoryIdAndYearAndMonth(
                userId, req.categoryId(), req.year(), req.month())
            .orElse(MonthlyBudget.builder()
                .user(User.builder().id(userId).build())
                .category(category)
                .year(req.year())
                .month(req.month())
                .build());

        budget.setLimitAmount(req.limitAmount());
        budget.setAlertThreshold(req.alertThreshold());
        budgetRepository.save(budget);

        return buildBudgetStatus(budget, userId);
    }

    public BankingDTO.BudgetStatusResponse getBudgetStatus(
            Long userId, Long categoryId, int year, int month) {

        var budget = budgetRepository
            .findByUserIdAndCategoryIdAndYearAndMonth(userId, categoryId, year, month)
            .orElseThrow(() -> new EntityNotFoundException("Budget not found"));

        return buildBudgetStatus(budget, userId);
    }

    private BankingDTO.BudgetStatusResponse buildBudgetStatus(
            MonthlyBudget budget, Long userId) {

        var start = LocalDateTime.of(budget.getYear(), budget.getMonth(), 1, 0, 0);
        var end   = start.plusMonths(1);

        BigDecimal spent = budget.getCategory() == null
            ? transactionRepository.sumAmountOutByUserAndDateRange(userId, start, end)
            : transactionRepository.sumAmountOutByUserAndCategoryAndDateRange(
                userId, budget.getCategory().getId(), start, end);

        if (spent == null) spent = BigDecimal.ZERO;

        int usagePercent = budget.getLimitAmount().compareTo(BigDecimal.ZERO) > 0
            ? spent.multiply(BigDecimal.valueOf(100))
                   .divide(budget.getLimitAmount(), 0, RoundingMode.HALF_UP)
                   .intValue()
            : 0;

        boolean alert = usagePercent >= budget.getAlertThreshold();

        if (alert) {
            kafkaTemplate.send("banking.sepay.budget-alert", userId.toString(),
                new BudgetAlertEvent(budget.getId(), usagePercent));
        }

        return new BankingDTO.BudgetStatusResponse(
            budget.getId(),
            budget.getCategory() != null ? budget.getCategory().getName() : "Tổng",
            budget.getYear(),
            budget.getMonth(),
            budget.getLimitAmount(),
            spent,
            budget.getLimitAmount().subtract(spent),
            usagePercent,
            alert
        );
    }

    // ── Reports ────────────────────────────────────────────────────────────

    public BankingDTO.MonthlySummaryResponse getMonthlySummary(
            Long userId, int year, int month) {

        var start = LocalDateTime.of(year, month, 1, 0, 0);
        var end   = start.plusMonths(1);

        BigDecimal totalIn  = coalesce(
            transactionRepository.sumAmountInByUserAndDateRange(userId, start, end));
        BigDecimal totalOut = coalesce(
            transactionRepository.sumAmountOutByUserAndDateRange(userId, start, end));

        List<Object[]> rows = transactionRepository
            .sumOutByCategoryAndDateRange(userId, start, end);

        List<BankingDTO.MonthlySummaryResponse.CategoryBreakdown> breakdown =
            rows.stream().map(r -> {
                String catName = r[0] != null ? (String) r[0] : "Khác";
                String color   = r[1] != null ? (String) r[1] : "#999999";
                BigDecimal amt = (BigDecimal) r[2];
                int count      = ((Number) r[3]).intValue();
                int share = totalOut.compareTo(BigDecimal.ZERO) > 0
                    ? amt.multiply(BigDecimal.valueOf(100))
                         .divide(totalOut, 0, RoundingMode.HALF_UP).intValue()
                    : 0;
                return new BankingDTO.MonthlySummaryResponse.CategoryBreakdown(
                    catName, color, amt, count, share);
            }).toList();

        return new BankingDTO.MonthlySummaryResponse(
            year, month, totalIn, totalOut,
            totalIn.subtract(totalOut), breakdown);
    }

    private BigDecimal coalesce(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
```

---

## 7. Webhook Controller

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
     * Xác thực qua header Authorization: Apikey {WEBHOOK_SECRET}
     */
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> receiveWebhook(
            @RequestBody BankingDTO.SepayWebhookPayload payload,
            @RequestHeader(value = "Authorization", required = false) String auth) {

        if (!isValidWebhookAuth(auth)) {
            log.warn("sepay_webhook_unauthorized");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("success", false, "message", "Unauthorized"));
        }

        log.info("sepay_webhook_received account={} amountIn={} amountOut={}",
            payload.accountNumber(), payload.amountIn(), payload.amountOut());

        webhookService.process(payload);

        return ResponseEntity.ok(Map.of("success", true));
    }

    private boolean isValidWebhookAuth(String auth) {
        return ("Apikey " + props.webhookSecret()).equals(auth);
    }
}
```

### 7.1 SepayWebhookService

```java
@Service @Slf4j @RequiredArgsConstructor
public class SepayWebhookService {

    private final SepayTransactionRepository transactionRepository;
    private final SepayBankAccountRepository bankAccountRepository;
    private final SpendingCategoryService categoryService;
    private final PersonalFinanceService financeService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public void process(BankingDTO.SepayWebhookPayload payload) {
        // Idempotency — tránh xử lý duplicate
        // SePay webhook không trả về id, dùng referenceCode làm dedup key
        if (transactionRepository.existsByReferenceNumber(payload.referenceCode())) {
            log.info("sepay_webhook_duplicate ref={}", payload.referenceCode());
            return;
        }

        var account = bankAccountRepository
            .findByAccountNumber(payload.accountNumber())
            .orElseGet(() -> {
                // Tài khoản chưa được đăng ký trong hệ thống — bỏ qua
                log.warn("sepay_webhook_unknown_account account={}",
                    payload.accountNumber());
                return null;
            });

        if (account == null) return;

        var direction = payload.amountIn() != null
            && payload.amountIn().compareTo(BigDecimal.ZERO) > 0
            ? TransactionDirection.IN : TransactionDirection.OUT;

        var category = categoryService.autoClassify(payload.content());

        var tx = SepayTransaction.builder()
            .sepayId(null)                  // webhook không trả sepay id
            .user(account.getUser())
            .sepayBankAccount(account)
            .accountNumber(payload.accountNumber())
            .bankBrandName(payload.gateway())
            .transactionDate(parseDate(payload.transactionDate()))
            .amountIn(coalesce(payload.amountIn()))
            .amountOut(coalesce(payload.amountOut()))
            .accumulated(coalesce(payload.accumulated()))
            .transactionContent(payload.content())
            .referenceNumber(payload.referenceCode())
            .code(payload.code())
            .subAccount(payload.subAccount())
            .direction(direction)
            .category(category)
            .source(SyncSource.WEBHOOK)
            .build();

        transactionRepository.save(tx);
        log.info("sepay_webhook_saved account={} direction={} amount={}",
            payload.accountNumber(), direction,
            direction == TransactionDirection.IN ? payload.amountIn() : payload.amountOut());

        kafkaTemplate.send("banking.sepay.transaction",
            account.getUser().getId().toString(),
            new SepayTransactionEvent(tx.getId(), direction.name()));
    }

    private LocalDateTime parseDate(String raw) {
        return LocalDateTime.parse(raw,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private BigDecimal coalesce(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
```

---

## 8. Personal Finance Controller

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
        return ResponseEntity.ok(categoryService.getCategories(u.getUserId()));
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

## 9. Repositories (JPQL chính)

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

## 10. Kafka Events & Topics

```java
public record SepayTransactionEvent(Long transactionId, String direction) {}
public record SepaySyncEvent(Long bankAccountId, int newTransactionCount) {}
public record BudgetAlertEvent(Long budgetId, int usagePercent) {}
```

```properties
# application.properties
banking.kafka.topics.sepay-transaction=banking.sepay.transaction
banking.kafka.topics.sepay-sync=banking.sepay.sync
banking.kafka.topics.sepay-budget-alert=banking.sepay.budget-alert
```

---

## 11. SecurityConfig

```java
.requestMatchers("/api/v1/personal-finance/**").hasAnyRole("CUSTOMER", "ADMIN")
.requestMatchers("/api/v1/sepay/webhook").permitAll()  // SePay gọi không có JWT
```

> **Lưu ý bảo mật:** endpoint `/webhook` phải dùng `permitAll()` nhưng
> được bảo vệ bởi **Apikey header** trong `SepayWebhookController`.

---

## 12. Data Seeder — System Categories

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

## 13. Exception

```java
public class SepayApiException extends RuntimeException {
    public SepayApiException(String message) { super(message); }
    public SepayApiException(String message, Throwable cause) { super(message, cause); }
}
```

---

## 14. Testing Strategy

### Unit Tests

```java
@ExtendWith(MockitoExtension.class)
class SepayWebhookServiceTest {

    @Mock SepayTransactionRepository transactionRepository;
    @Mock SepayBankAccountRepository bankAccountRepository;
    @Mock SpendingCategoryService categoryService;
    @Mock KafkaTemplate<String, Object> kafkaTemplate;
    @InjectMocks SepayWebhookService service;

    @Test
    void process_whenDuplicateReferenceCode_skipsProcessing() {
        given(transactionRepository.existsByReferenceNumber("REF001")).willReturn(true);
        service.process(buildPayload("REF001"));
        then(transactionRepository).should(never()).save(any());
    }

    @Test
    void process_whenUnknownAccount_skipsProcessing() {
        given(transactionRepository.existsByReferenceNumber(any())).willReturn(false);
        given(bankAccountRepository.findByAccountNumber(any())).willReturn(Optional.empty());
        service.process(buildPayload("REF002"));
        then(transactionRepository).should(never()).save(any());
    }

    @Test
    void process_success_savesTransactionAndPublishesKafkaEvent() {
        var account = mock(SepayBankAccount.class);
        given(account.getUser()).willReturn(User.builder().id(1L).build());
        given(transactionRepository.existsByReferenceNumber(any())).willReturn(false);
        given(bankAccountRepository.findByAccountNumber(any()))
            .willReturn(Optional.of(account));

        service.process(buildPayload("REF003"));

        then(transactionRepository).should().save(any(SepayTransaction.class));
        then(kafkaTemplate).should().send(eq("banking.sepay.transaction"), any(), any());
    }

    private BankingDTO.SepayWebhookPayload buildPayload(String ref) {
        return new BankingDTO.SepayWebhookPayload(
            "Vietcombank", "2024-01-15 10:30:00", "0071000888888",
            null, new BigDecimal("500000"), BigDecimal.ZERO,
            new BigDecimal("5500000"), null,
            "NGUYEN VAN A chuyen khoan", ref, null);
    }
}

@ExtendWith(MockitoExtension.class)
class SpendingCategoryServiceTest {

    @Mock SpendingCategoryRepository categoryRepository;
    @InjectMocks SpendingCategoryService service;

    @Test
    void autoClassify_whenContentContainsGrab_returnsDiChuyenCategory() {
        var diChuyen = SpendingCategory.builder().name("Di chuyển")
            .keywords(List.of("grab", "taxi")).build();
        given(categoryRepository.findAllWithKeywords()).willReturn(List.of(diChuyen));

        var result = service.autoClassify("Grab - payment for ride");

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Di chuyển");
    }

    @Test
    void autoClassify_whenNoKeywordMatch_returnsNull() {
        given(categoryRepository.findAllWithKeywords()).willReturn(List.of());
        assertThat(service.autoClassify("unknown content")).isNull();
    }
}

@ExtendWith(MockitoExtension.class)
class PersonalFinanceServiceTest {

    @Mock SepayTransactionRepository transactionRepository;
    @Mock MonthlyBudgetRepository budgetRepository;
    @Mock SpendingCategoryRepository categoryRepository;
    @Mock KafkaTemplate<String, Object> kafkaTemplate;
    @InjectMocks PersonalFinanceService service;

    @Test
    void getBudgetStatus_whenUsageExceedsThreshold_firesKafkaAlert() {
        var budget = MonthlyBudget.builder()
            .id(1L).year(2024).month(1)
            .limitAmount(new BigDecimal("1000000"))
            .alertThreshold(80)
            .build();
        given(budgetRepository.findByUserIdAndCategoryIdAndYearAndMonth(any(), any(), any(), any()))
            .willReturn(Optional.of(budget));
        given(transactionRepository.sumAmountOutByUserAndDateRange(any(), any(), any()))
            .willReturn(new BigDecimal("900000"));  // 90% → triggers alert

        service.getBudgetStatus(1L, null, 2024, 1);

        then(kafkaTemplate).should().send(eq("banking.sepay.budget-alert"), any(), any());
    }
}
```

### Integration Test với Testcontainers

```java
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PersonalFinanceIntegrationTest {

    @Autowired PersonalFinanceController controller;
    @Autowired SepayBankAccountRepository bankAccountRepository;

    @Test
    void fullFlow_addAccount_triggerSync_getReport() {
        // 1. POST /api/v1/personal-finance/bank-accounts
        // 2. POST /api/v1/personal-finance/bank-accounts/{id}/sync
        //    (mock SepayApiClient hoặc dùng WireMock)
        // 3. GET  /api/v1/personal-finance/reports/monthly → verify totalIn/Out
    }

    @Test
    void webhook_receiveTransaction_classifiesAndSavesCorrently() {
        // 1. Setup SepayBankAccount trong DB
        // 2. POST /api/v1/sepay/webhook với payload hợp lệ
        // 3. Verify transaction saved với đúng direction + category
    }

    @Test
    void budget_createBudget_exceedThreshold_triggersAlert() {
        // 1. Tạo budget 1,000,000 với alertThreshold=80
        // 2. Insert transactions tổng 900,000 (90%)
        // 3. GET /api/v1/personal-finance/budgets → alertTriggered = true
    }
}
```

---

## 15. Checklist triển khai

- [ ] Thêm `SEPAY_API_TOKEN` và `SEPAY_WEBHOOK_SECRET` vào `.env` / Vault
- [ ] Thêm 4 Entity mới: `SepayBankAccount`, `SepayTransaction`, `SpendingCategory`, `MonthlyBudget`
- [ ] Thêm 3 Enum mới: `SepayAccountStatus`, `TransactionDirection`, `SyncSource`
- [ ] Thêm DTOs vào `BankingDTO.java` (SePay API + Webhook + Application DTOs)
- [ ] Thêm `SepayProperties` với `@ConfigurationProperties`
- [ ] Tạo `SepayApiClient` (RestClient + Guava RateLimiter — dep: `com.google.guava:guava`)
- [ ] Tạo `SepayTransactionSyncService` với `@Scheduled`
- [ ] Thêm `@EnableScheduling` vào main class
- [ ] Tạo `SpendingCategoryService` (auto-classify bằng keyword)
- [ ] Tạo `PersonalFinanceService` (budget + report)
- [ ] Tạo `SepayWebhookService`
- [ ] Tạo `SepayWebhookController` tại `/api/v1/sepay/webhook`
- [ ] Tạo `PersonalFinanceController` tại `/api/v1/personal-finance`
- [ ] Tạo `SepayBankAccountService`, `SepayBankAccountRepository`
- [ ] Thêm JPQL queries vào `SepayTransactionRepository`
- [ ] Cập nhật `SecurityConfig` (`/api/v1/personal-finance/**` và `/api/v1/sepay/webhook`)
- [ ] Tạo `SpendingCategorySeeder`
- [ ] Thêm 3 Kafka topics vào `application.properties`
- [ ] Tạo `SepayApiException`
- [ ] Đăng ký Webhook URL tại `my.sepay.vn` → Webhooks → Thêm webhook
- [ ] Chạy `mvn clean install` và `mvn test`
- [ ] Test Swagger UI: tag "Personal Finance"

> **Lưu ý quan trọng:**
> - Webhook endpoint KHÔNG dùng JWT → cần `permitAll()` trong SecurityConfig,
    >   bảo vệ bằng Apikey header
> - `since_id` trong Pull API cho phép incremental sync, tránh kéo lại toàn bộ lịch sử
> - Rate limit SePay 3 req/s — Guava `RateLimiter.create(3)` đảm bảo không bị HTTP 429
> - `sepayId` từ webhook không có → dùng `referenceNumber` làm dedup key cho webhook;
    >   dùng `sepayId` cho Pull API
> - Không bao giờ log `SEPAY_API_TOKEN` — mask trước khi ghi log