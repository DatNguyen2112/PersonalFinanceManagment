package BankingSystem.Repositories;

import BankingSystem.Entity.SpendingCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpendingCategoryRepository extends JpaRepository<SpendingCategory, Long> {
    @Query("""
    SELECT DISTINCT sc FROM SpendingCategory sc
    LEFT JOIN FETCH sc.keywords
    WHERE sc.system = true OR sc.user.id = :userId
    """)
    List<SpendingCategory> findSystemAndUserCategories(@Param("userId") Long userId);

    // Fetch kèm keywords để tránh LazyInitializationException trong autoClassify
    @Query("""
        SELECT DISTINCT c FROM SpendingCategory c
        LEFT JOIN FETCH c.keywords
        ORDER BY c.system DESC
        """)
    List<SpendingCategory> findAllWithKeywords();

    long countBySystemTrue();
}

