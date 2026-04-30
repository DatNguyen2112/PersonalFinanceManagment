package BankingSystem.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

