package BankingSystem.Entity.SepayAccount;

import BankingSystem.Entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDateTime;


@Entity
@Table(name = "sepay_bank_accounts",
        indexes = {
                @Index(name = "idx_sepay_user", columnList = "user_id"),
                @Index(name = "idx_sepay_account", columnList = "account_number", unique = true)
        })
@EntityListeners(AuditingEntityListener.class)
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SepayAccount {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "account_number", nullable = false, unique = true, length = 30)
    private String accountNumber;

    @Column(name = "bank_brand_name", length = 50)
    private String bankBrandName;

    @Column(name = "bank_account_id")
    private Long sepayBankAccountId;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "last_synced_transaction_id")
    private Long lastSyncedTransactionId;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SepayAccountStatus status = SepayAccountStatus.ACTIVE;

    @CreatedDate private LocalDateTime createdAt;
    @LastModifiedDate private LocalDateTime updatedAt;
}

