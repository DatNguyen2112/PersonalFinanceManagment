package BankingSystem.Repositories;

import BankingSystem.Entity.SepayTransaction.SepayTransaction;
import BankingSystem.Entity.SepayTransaction.TransactionDirection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
        SELECT t FROM SepayTransaction t
        LEFT JOIN FETCH t.category
        WHERE t.user.id = :userId
          AND (:accountNumber IS NULL OR t.accountNumber = :accountNumber)
          AND (:categoryId    IS NULL OR t.category.id  = :categoryId)
          AND (:direction     IS NULL OR t.direction    = :direction)
          AND (:dateMin       IS NULL OR t.transactionDate >= :dateMin)
          AND (:dateMax       IS NULL OR t.transactionDate <= :dateMax)
        """)
    Page<SepayTransaction> findByUserIdWithFilters(
            @Param("userId")        Long userId,
            @Param("accountNumber") String accountNumber,
            @Param("categoryId")    Long categoryId,
            @Param("direction") TransactionDirection direction,
            @Param("dateMin")       LocalDateTime dateMin,
            @Param("dateMax")       LocalDateTime dateMax,
            PageRequest pageable);

    @Query("""
            SELECT COALESCE(SUM(t.amountIn), 0)
            FROM SepayTransaction t
            WHERE t.user.id = :userId
              AND t.amountIn > 0
              AND t.transactionDate >= :start
              AND t.transactionDate < :end
              AND t.direction = :direction
            """)
    BigDecimal sumAmountOutByUserAndDateRange(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("direction") TransactionDirection direction);

    @Query("""
            SELECT COALESCE(SUM(t.amountIn), 0)
            FROM SepayTransaction t
            WHERE t.user.id = :userId
              AND t.amountIn > 0
              AND t.category.id = :categoryId
              AND t.transactionDate >= :start
              AND t.transactionDate < :end
              AND t.direction= :direction
            """)
    BigDecimal sumAmountOutByUserAndCategoryAndDateRange(
            @Param("userId") Long userId,
            @Param("categoryId") Long categoryId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("direction") TransactionDirection direction);

    @Query("""
            SELECT COALESCE(SUM(t.amountIn), 0)
            FROM SepayTransaction t
            WHERE t.user.id = :userId
              AND t.amountIn > 0
              AND t.transactionDate >= :start
              AND t.transactionDate < :end
              AND t.direction = :direction
            """)
    BigDecimal sumAmountInByUserAndDateRange(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("direction") TransactionDirection direction);

    @Query("""
            SELECT c.name, c.color, SUM(t.amountIn), COUNT(t)
            FROM SepayTransaction t
            LEFT JOIN t.category c
            WHERE t.user.id = :userId
              AND t.amountIn > 0
              AND t.transactionDate >= :start
              AND t.transactionDate < :end
              AND t.direction = :direction
            GROUP BY c.name, c.color
            ORDER BY SUM(t.amountIn) DESC
            """)
    List<Object[]> sumOutByCategoryAndDateRange(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("direction") TransactionDirection direction);

     @Query("""
       SELECT c.name, c.color, SUM(t.amountIn), COUNT(t)
       FROM SepayTransaction t
       LEFT JOIN t.category c
       WHERE t.user.id = :userId
         AND t.amountIn > 0
         AND t.transactionDate >= :start
         AND t.transactionDate < :end
         AND t.direction = :direction
       GROUP BY c.name, c.color
       ORDER BY SUM(t.amountIn) DESC
     """)
     List<Object[]> sumInByCategoryAndDateRange(
             Long userId, LocalDateTime start, LocalDateTime end, TransactionDirection direction);
}
