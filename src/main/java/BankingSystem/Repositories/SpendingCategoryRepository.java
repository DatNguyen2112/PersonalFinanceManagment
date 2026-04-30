package BankingSystem.Repositories;

import BankingSystem.Entity.SpendingCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpendingCategoryRepository extends JpaRepository<SpendingCategory, Long> {
    List<SpendingCategory> findBySystemTrue(Long userId);

    // Trả về cả danh mục hệ thống lẫn danh mục của user
    @Query("""
        SELECT c FROM SpendingCategory c
        WHERE c.system = true
           OR c.user.id = :userId
        ORDER BY c.system DESC, c.name ASC
        """)
    List<SpendingCategory> findByUserIdOrSystemTrue(@Param("userId") Long userId);

    // Fetch kèm keywords để tránh LazyInitializationException trong autoClassify
    @Query("""
        SELECT DISTINCT c FROM SpendingCategory c
        LEFT JOIN FETCH c.keywords
        ORDER BY c.system DESC
        """)
    List<SpendingCategory> findAllWithKeywords();

    long countBySystemTrue();
}

