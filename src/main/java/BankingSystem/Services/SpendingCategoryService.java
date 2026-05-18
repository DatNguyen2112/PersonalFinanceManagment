package BankingSystem.Services;

import BankingSystem.Config.DataSeeder.SpendingCategorySeeder;
import BankingSystem.Entity.SpendingCategory;
import BankingSystem.Repositories.SpendingCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class SpendingCategoryService {
    private final SpendingCategoryRepository categoryRepository;
    private final SpendingCategorySeeder seeder;

    /**
     * Phân loại giao dịch dựa trên keyword trong nội dung.
     * Priority: user-defined > system categories.
     * Trả về null nếu không khớp (để UI hiển thị "Khác").
     */
    public SpendingCategory autoClassify(String content) {
        if (content == null) return null;
        String lower = content.toLowerCase();

        seeder.run();

        return categoryRepository.findAllWithKeywords().stream()
                .filter(cat -> cat.getKeywords().stream()
                        .anyMatch(kw -> lower.contains(kw.toLowerCase())))
                .findFirst()
                .orElse(null);
    }

    public List<SpendingCategory> getSystemCategories(Long userId) {
        return categoryRepository.findBySystemTrue(userId);
    }
}
