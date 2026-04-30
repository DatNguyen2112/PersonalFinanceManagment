package BankingSystem.Repositories;

import BankingSystem.Entity.SpendingCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpendingCategoryRepository extends JpaRepository<SpendingCategory, Long> {
    List<SpendingCategory> findAllWithKeywords();
    List<SpendingCategory> findBySystemTrue();
}

