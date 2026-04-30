package BankingSystem.Repositories;

import BankingSystem.Entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long> {

    // ── Single lookup ──────────────────────────────────────────────────────

    Optional<Budget> findByUserIdAndCategoryIdAndYearAndMonth(
            Long userId, Long categoryId, int year, int month);

    @Query("""
            SELECT b FROM Budget b
            WHERE b.user.id = :userId
              AND b.category IS NULL
              AND b.year = :year
              AND b.month = :month
            """)
    Optional<Budget> findTotalBudgetByUserAndYearAndMonth(
            @Param("userId") Long userId,
            @Param("year") int year,
            @Param("month") int month);

    // ── List ───────────────────────────────────────────────────────────────

    @Query("""
            SELECT b FROM Budget b
            LEFT JOIN FETCH b.category
            WHERE b.user.id = :userId
              AND b.year = :year
              AND b.month = :month
            ORDER BY b.category.name ASC NULLS FIRST
            """)
    List<Budget> findByUserIdAndYearAndMonth(
            @Param("userId") Long userId,
            @Param("year") int year,
            @Param("month") int month);

    @Query("""
            SELECT b FROM Budget b
            LEFT JOIN FETCH b.category
            WHERE b.user.id = :userId
            ORDER BY b.year DESC, b.month DESC
            """)
    List<Budget> findAllByUserId(@Param("userId") Long userId);

    // ── Existence check ────────────────────────────────────────────────────

    boolean existsByUserIdAndCategoryIdAndYearAndMonth(
            Long userId, Long categoryId, int year, int month);

    // ── Delete ─────────────────────────────────────────────────────────────

    void deleteByUserIdAndCategoryIdAndYearAndMonth(
            Long userId, Long categoryId, int year, int month);

    // ── Alert check — lấy các budget sắp vượt ngưỡng ─────────────────────

    @Query("""
            SELECT b FROM Budget b
            LEFT JOIN FETCH b.category
            WHERE b.user.id = :userId
              AND b.year = :year
              AND b.month = :month
              AND b.limitAmount > 0
            """)
    List<Budget> findActiveBudgetsForMonth(
            @Param("userId") Long userId,
            @Param("year") int year,
            @Param("month") int month);
}

