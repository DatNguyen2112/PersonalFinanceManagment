package BankingSystem.Services;

import BankingSystem.Config.Kafka.KafkaEventConfig;
import BankingSystem.DTO.BankingDTO;
import BankingSystem.Entity.Budget;
import BankingSystem.Entity.SepayTransaction.SepayTransaction;
import BankingSystem.Entity.SepayTransaction.TransactionDirection;
import BankingSystem.Entity.SpendingCategory;
import BankingSystem.Entity.User;
import BankingSystem.Repositories.BudgetRepository;
import BankingSystem.Repositories.SepayTransactionRepository;
import BankingSystem.Repositories.SpendingCategoryRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PersonalFinanceService {

    private final SepayTransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final SpendingCategoryRepository categoryRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // ── Budget ─────────────────────────────────────────────────────────────

    @Transactional
    public BankingDTO.BudgetStatusResponse createOrUpdateBudget(
            Long userId, BankingDTO.BudgetRequest req) {

        var category = resolveCategory(req.categoryId());

        var budget = findExistingBudget(userId, req.categoryId(), req.year(), req.month())
                .orElseGet(() -> Budget.builder()
                        .user(User.builder().id(userId).build())
                        .category(category)
                        .year(req.year())
                        .month(req.month())
                        .build());

        budget.setLimitAmount(req.limitAmount());
        budget.setAlertThreshold(req.alertThreshold());
        budgetRepository.save(budget);

        log.info("budget_saved userId={} categoryId={} year={} month={} limit={}",
                userId, req.categoryId(), req.year(), req.month(), req.limitAmount());

        return buildBudgetStatus(budget, userId);
    }

    @Transactional(readOnly = true)
    public BankingDTO.BudgetStatusResponse getBudgetStatus(
            Long userId, Long categoryId, int year, int month) {

        var budget = findExistingBudget(userId, categoryId, year, month)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Budget not found for categoryId=%s year=%d month=%d"
                                .formatted(categoryId, year, month)));

        return buildBudgetStatus(budget, userId);
    }

    @Transactional(readOnly = true)
    public List<BankingDTO.BudgetStatusResponse> getBudgets(
            Long userId, int year, int month) {

        return budgetRepository.findByUserIdAndYearAndMonth(userId, year, month)
                .stream()
                .map(budget -> buildBudgetStatus(budget, userId))
                .toList();
    }

    @Transactional
    public void deleteBudget(Long userId, Long categoryId, int year, int month) {
        if (!existsBudget(userId, categoryId, year, month)) {
            throw new EntityNotFoundException(
                    "Budget not found for categoryId=%s year=%d month=%d"
                            .formatted(categoryId, year, month));
        }
        budgetRepository.deleteByUserIdAndCategoryIdAndYearAndMonth(
                userId, categoryId, year, month);

        log.info("budget_deleted userId={} categoryId={} year={} month={}",
                userId, categoryId, year, month);
    }

    // ── Reports ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public BankingDTO.MonthlySummaryResponse getMonthlySummary(
            Long userId, int year, int month) {

        var range = DateRange.ofMonth(year, month);

        var totalIn  = coalesce(transactionRepository
                .sumAmountInByUserAndDateRange(userId, range.start(), range.end()));
        var totalOut = coalesce(transactionRepository
                .sumAmountOutByUserAndDateRange(userId, range.start(), range.end()));

        var breakdown = buildCategoryBreakdown(userId, range, totalOut);

        log.info("monthly_summary_fetched userId={} year={} month={} in={} out={}",
                userId, year, month, totalIn, totalOut);

        return new BankingDTO.MonthlySummaryResponse(
                year, month, totalIn, totalOut,
                totalIn.subtract(totalOut),
                breakdown);
    }

    @Transactional(readOnly = true)
    public Page<BankingDTO.TransactionResponse> getTransactions(
            Long userId, BankingDTO.TransactionQueryRequest req) {

        var pageable = PageRequest.of(req.page(), req.size(),
                Sort.by(Sort.Direction.DESC, "transactionDate"));

        return transactionRepository
                .findByUserIdWithFilters(
                        userId,
                        req.accountNumber(),
                        req.categoryId(),
                        req.direction() != null
                                ? TransactionDirection.valueOf(req.direction()) : null,
                        req.dateMin() != null
                                ? req.dateMin().atStartOfDay() : null,
                        req.dateMax() != null
                                ? req.dateMax().atTime(23, 59, 59) : null,
                        pageable)
                .map(this::mapToTransactionResponse);
    }

    @Transactional
    public BankingDTO.TransactionResponse updateCategory(
            Long transactionId, Long categoryId, Long userId) {

        var tx = transactionRepository.findById(transactionId)
                .filter(t -> t.getUser().getId().equals(userId))
                .orElseThrow(() -> new EntityNotFoundException(
                        "Transaction not found id=" + transactionId));

        var category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Category not found id=" + categoryId));

        tx.setCategory(category);
        transactionRepository.save(tx);

        log.info("transaction_category_updated txId={} categoryId={}", transactionId, categoryId);

        return mapToTransactionResponse(tx);
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private SpendingCategory resolveCategory(Long categoryId) {
        if (categoryId == null) return null;
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Category not found id=" + categoryId));
    }

    private Optional<Budget> findExistingBudget(
            Long userId, Long categoryId, int year, int month) {

        return categoryId != null
                ? budgetRepository.findByUserIdAndCategoryIdAndYearAndMonth(
                userId, categoryId, year, month)
                : budgetRepository.findTotalBudgetByUserAndYearAndMonth(
                userId, year, month);
    }

    private boolean existsBudget(Long userId, Long categoryId, int year, int month) {
        return categoryId != null
                ? budgetRepository.existsByUserIdAndCategoryIdAndYearAndMonth(
                userId, categoryId, year, month)
                : budgetRepository.findTotalBudgetByUserAndYearAndMonth(userId, year, month)
                .isPresent();
    }

    private BankingDTO.BudgetStatusResponse buildBudgetStatus(
            Budget budget, Long userId) {

        var range = DateRange.ofMonth(budget.getYear(), budget.getMonth());

        var spent = budget.getCategory() == null
                ? coalesce(transactionRepository.sumAmountOutByUserAndDateRange(
                userId, range.start(), range.end()))
                : coalesce(transactionRepository.sumAmountOutByUserAndCategoryAndDateRange(
                userId, budget.getCategory().getId(), range.start(), range.end()));

        int usagePercent = computeUsagePercent(spent, budget.getLimitAmount());
        boolean alert    = usagePercent >= budget.getAlertThreshold();

        if (alert) {
            log.warn("budget_alert_triggered userId={} budgetId={} usage={}%",
                    userId, budget.getId(), usagePercent);
            kafkaTemplate.send(
                    "banking.sepay.budget-alert",
                    userId.toString(),
                    new KafkaEventConfig.BudgetAlertEvent(budget.getId(), usagePercent));
        }

        var remaining = budget.getLimitAmount().subtract(spent);
        var categoryName = budget.getCategory() != null
                ? budget.getCategory().getName() : "Tổng";

        return new BankingDTO.BudgetStatusResponse(
                budget.getId(),
                categoryName,
                budget.getYear(),
                budget.getMonth(),
                budget.getLimitAmount(),
                spent,
                remaining,
                usagePercent,
                alert);
    }

    private List<BankingDTO.MonthlySummaryResponse.CategoryBreakdown> buildCategoryBreakdown(
            Long userId, DateRange range, BigDecimal totalOut) {

        return transactionRepository
                .sumOutByCategoryAndDateRange(userId, range.start(), range.end())
                .stream()
                .map(row -> {
                    String catName = row[0] != null ? (String) row[0] : "Khác";
                    String color   = row[1] != null ? (String) row[1] : "#999999";
                    var amt        = (BigDecimal) row[2];
                    int count      = ((Number) row[3]).intValue();
                    int share      = computeUsagePercent(amt, totalOut);
                    return new BankingDTO.MonthlySummaryResponse.CategoryBreakdown(
                            catName, color, amt, count, share);
                })
                .toList();
    }

    private BankingDTO.TransactionResponse mapToTransactionResponse(SepayTransaction tx) {
        return new BankingDTO.TransactionResponse(
                tx.getId(),
                tx.getSepayId(),
                tx.getAccountNumber(),
                tx.getBankBrandName(),
                tx.getTransactionDate(),
                tx.getAmountIn(),
                tx.getAmountOut(),
                tx.getAccumulated(),
                tx.getTransactionContent(),
                tx.getReferenceNumber(),
                tx.getCode(),
                tx.getCategory() != null ? tx.getCategory().getName() : null,
                tx.getNote(),
                tx.getDirection().name(),
                tx.getSource().name());
    }

    private int computeUsagePercent(BigDecimal spent, BigDecimal limit) {
        if (limit == null || limit.compareTo(BigDecimal.ZERO) <= 0) return 0;
        return spent.multiply(BigDecimal.valueOf(100))
                .divide(limit, 0, RoundingMode.HALF_UP)
                .intValue();
    }

    private BigDecimal coalesce(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    // ── Inner value object ─────────────────────────────────────────────────

    private record DateRange(LocalDateTime start, LocalDateTime end) {
        static DateRange ofMonth(int year, int month) {
            var start = LocalDateTime.of(year, month, 1, 0, 0);
            return new DateRange(start, start.plusMonths(1));
        }
    }
}
