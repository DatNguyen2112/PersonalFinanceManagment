package BankingSystem.Repositories;

import BankingSystem.Entity.SepayTransaction.SepayTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
