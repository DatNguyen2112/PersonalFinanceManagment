package BankingSystem.Config.DataSeeder;

import BankingSystem.Entity.SpendingCategory;
import BankingSystem.Repositories.SpendingCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SpendingCategorySeeder implements CommandLineRunner {

    private final SpendingCategoryRepository categoryRepository;

    private static final List<SeedData> SYSTEM_CATEGORIES = List.of(
            new SeedData("Ăn uống",      "🍜", "#FF6B6B",
                    List.of("quan an", "nha hang", "cafe", "food", "pho", "com", "bun", "bia", "nuoc")),
            new SeedData("Di chuyển",    "🚗", "#4ECDC4",
                    List.of("grab", "taxi", "xe", "xang", "dau", "parking", "bus", "vinbus")),
            new SeedData("Mua sắm",      "🛍️", "#45B7D1",
                    List.of("shopee", "lazada", "tiki", "sendo", "mua hang", "sieu thi", "vinmart")),
            new SeedData("Hóa đơn",      "💡", "#96CEB4",
                    List.of("dien", "nuoc", "internet", "dtv", "evn", "vnpt", "viettel", "fpt")),
            new SeedData("Giải trí",     "🎬", "#FFEAA7",
                    List.of("cinema", "rap phim", "game", "netflix", "youtube premium", "spotify")),
            new SeedData("Sức khỏe",     "💊", "#DDA0DD",
                    List.of("nha thuoc", "benh vien", "kham benh", "thuoc", "bvdc", "medic")),
            new SeedData("Giáo dục",     "📚", "#98D8C8",
                    List.of("hoc phi", "sach", "khoa hoc", "udemy", "coursera", "truong")),
            new SeedData("Chuyển khoản", "💸", "#B0C4DE",
                    List.of("chuyen khoan", "chuyen tien", "transfer"))
    );

    @Override
    public void run(String... args) {
        if (categoryRepository.countBySystemTrue() > 0) return;

        SYSTEM_CATEGORIES.forEach(data -> categoryRepository.save(
                SpendingCategory.builder()
                        .name(data.name()).iconCode(data.icon()).color(data.color())
                        .system(true).keywords(data.keywords())
                        .build()
        ));

        log.info("spending_categories_seeded count={}", SYSTEM_CATEGORIES.size());
    }

    private record SeedData(
            String name, String icon, String color, List<String> keywords) {}
}
