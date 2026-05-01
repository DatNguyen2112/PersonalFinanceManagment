package BankingSystem.Entity.SepayTransaction;

import BankingSystem.Entity.SepayAccount.SepayAccount;
import BankingSystem.Entity.SpendingCategory;
import BankingSystem.Entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SepayTransaction {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sepay_id", unique = true)
    private Long sepayId;                   // ID từ SePay — tránh duplicate

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sepay_bank_account_id")
    private SepayAccount sepayBankAccount;

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
    private SyncSourceType source = SyncSourceType.WEBHOOK;  // WEBHOOK | PULL_API

    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;
}
