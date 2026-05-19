package BankingSystem.Services;

import BankingSystem.Config.Kafka.KafkaEventConfig;
import BankingSystem.DTO.BankingDTO;
import BankingSystem.Entity.Budget;
import BankingSystem.Entity.SepayTransaction.SepayTransaction;
import BankingSystem.Entity.SepayTransaction.TransactionDirection;
import BankingSystem.Entity.SpendingCategory;
import BankingSystem.Entity.User;
import BankingSystem.Exception.BankingException;
import BankingSystem.Exception.BudgetNotFoundException;
import BankingSystem.Repositories.BudgetRepository;
import BankingSystem.Repositories.SepayTransactionRepository;
import BankingSystem.Repositories.SpendingCategoryRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

@Service
@Slf4j
@RequiredArgsConstructor
public class PersonalFinanceService {

    private final SepayTransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final SpendingCategoryRepository categoryRepository;
    private final KafkaProducerService kafkaProducerService;

    // ── Budget ─────────────────────────────────────────────────────────────

    @Transactional
    public BankingDTO.BudgetStatusResponse createOrUpdateBudget(
            Long userId, BankingDTO.BudgetRequest req) {
        try {
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

            log.info("budget_saved userId={} categoryId={} year={} month={}",
                    userId, req.categoryId(), req.year(), req.month());

            return buildBudgetStatus(budget, userId);

        } catch (BankingException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("budget_create_failed userId={} error={}", userId, ex.getMessage(), ex);
            throw new BankingException("BUDGET_CREATE_ERROR",
                    "Không thể tạo ngân sách", ex);
        }
    }

    @Transactional(readOnly = true)
    public BankingDTO.BudgetStatusResponse getBudgetStatus(
            Long userId, Long categoryId, int year, int month) {
        try {
            var budget = findExistingBudget(userId, categoryId, year, month)
                    .orElseThrow(() -> new BudgetNotFoundException(categoryId, year, month));
            return buildBudgetStatus(budget, userId);

        } catch (BankingException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("budget_get_failed userId={} error={}", userId, ex.getMessage(), ex);
            throw new BankingException("BUDGET_FETCH_ERROR",
                    "Không thể lấy thông tin ngân sách", ex);
        }
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
        try {
            var range = DateRange.ofMonth(year, month);
            var totalIn  = coalesce(transactionRepository
                    .sumAmountInByUserAndDateRange(userId, range.start(), range.end(), TransactionDirection.IN ));
            var totalOut = coalesce(transactionRepository
                    .sumAmountOutByUserAndDateRange(userId, range.start(), range.end(), TransactionDirection.OUT));
            var breakdown = buildCategoryBreakdown(userId, range, totalOut);

            return new BankingDTO.MonthlySummaryResponse(
                    year, month, totalIn, totalOut,
                    totalIn.subtract(totalOut), breakdown);

        } catch (Exception ex) {
            log.error("monthly_summary_failed userId={} year={} month={} error={}",
                    userId, year, month, ex.getMessage(), ex);
            throw new BankingException("REPORT_FETCH_ERROR",
                    "Không thể lấy báo cáo tháng", ex);
        }
    }

    @Transactional(readOnly = true)
    public Page<BankingDTO.TransactionResponse> getTransactions(
            Long userId, BankingDTO.TransactionQueryRequest req) {

        log.info("getTransactions userId={} req={}", userId, req);

        var pageable = PageRequest.of(Math.max(0, req.page() - 1), req.size(),
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

    // Thống kê dòng tiền 6 tháng gần nhất (tháng chưa có giao dịch → zero)
    @Transactional(readOnly = true)
    public List<BankingDTO.MonthlyBarData> getLast6MonthsCashFlow(Long userId) {
        var now    = LocalDate.now();
        var locale = new java.util.Locale("vi", "VN");

        return IntStream.rangeClosed(0, 5)
                .mapToObj(i -> now.minusMonths(5 - i))      // oldest → newest
                .map(d -> {
                    var range   = DateRange.ofMonth(d.getYear(), d.getMonthValue());
                    var income  = coalesce(transactionRepository
                            .sumAmountInByUserAndDateRange(userId, range.start(), range.end(), TransactionDirection.IN));
                    var expense = coalesce(transactionRepository
                            .sumAmountOutByUserAndDateRange(userId, range.start(), range.end(), TransactionDirection.OUT));
                    var label   = "Tháng " + d.getMonthValue();
                    return new BankingDTO.MonthlyBarData(label, d.getYear(), d.getMonthValue(),
                            income, expense);
                })
                .toList();
    }

    // Thống kê theo tổng quan dòng tiền theo năm
    @Transactional(readOnly = true)
    public BankingDTO.YearlySummaryResponse getYearlySummary(Long userId, int year) {
        try {
            var yearRange = DateRange.ofYear(year);

            var totalIncome  = coalesce(transactionRepository
                    .sumAmountInByUserAndDateRange(userId, yearRange.start(), yearRange.end(), TransactionDirection.IN));
            var totalExpense = coalesce(transactionRepository
                    .sumAmountOutByUserAndDateRange(userId, yearRange.start(), yearRange.end(), TransactionDirection.OUT));
            var netSavings   = totalIncome.subtract(totalExpense);

            int savingsRate = computeUsagePercent(netSavings, totalIncome);

            // Average over months that have actually started (up to current month if same year)
            int monthsElapsed = year == LocalDate.now().getYear()
                    ? LocalDate.now().getMonthValue()
                    : 12;

            var avgIn  = totalIncome.divide(BigDecimal.valueOf(monthsElapsed), 0, RoundingMode.HALF_UP);
            var avgOut = totalExpense.divide(BigDecimal.valueOf(monthsElapsed), 0, RoundingMode.HALF_UP);

            // Per-month bars (Jan–Dec; future months → zero)
            int maxMonth = year == LocalDate.now().getYear()
                    ? LocalDate.now().getMonthValue() : 12;

            var bars = IntStream.rangeClosed(1, 12)
                    .mapToObj(m -> {
                        if (m > maxMonth) {
                            return new BankingDTO.MonthlyBarData("Tháng " + m, year, m,
                                    BigDecimal.ZERO, BigDecimal.ZERO);
                        }
                        var r  = DateRange.ofMonth(year, m);
                        var in = coalesce(transactionRepository
                                .sumAmountInByUserAndDateRange(userId, r.start(), r.end(), TransactionDirection.IN));
                        var out = coalesce(transactionRepository
                                .sumAmountOutByUserAndDateRange(userId, r.start(), r.end(), TransactionDirection.OUT));
                        return new BankingDTO.MonthlyBarData("Tháng " + m, year, m, in, out);
                    })
                    .toList();

            return new BankingDTO.YearlySummaryResponse(
                    year, totalIncome, totalExpense, netSavings,
                    savingsRate, avgIn, avgOut, bars);

        } catch (Exception ex) {
            log.error("yearly_summary_failed userId={} year={} error={}",
                    userId, year, ex.getMessage(), ex);
            throw new BankingException("YEARLY_REPORT_ERROR",
                    "Không thể lấy báo cáo năm", ex);
        }
    }

    // Thống kê theo danh mục
    @Transactional(readOnly = true)
    public BankingDTO.CategoryReportResponse getCategoryReport(Long userId, int year) {
        try {
            var range = DateRange.ofYear(year);

            var totalExpense = coalesce(transactionRepository
                    .sumAmountOutByUserAndDateRange(userId, range.start(), range.end(), TransactionDirection.OUT));

            var expenseRows = transactionRepository
                    .sumOutByCategoryAndDateRange(userId, range.start(), range.end(), TransactionDirection.OUT);

            var expenseItems = expenseRows.stream()
                    .map(row -> {
                        String name  = row[0] != null ? (String) row[0] : "Khác";
                        String color = row[1] != null ? (String) row[1] : "#999999";
                        var amt      = (BigDecimal) row[2];
                        int pct      = computeUsagePercent(amt, totalExpense);
                        return new BankingDTO.CategoryItem(name, color, amt, pct);
                    })
                    .sorted(Comparator.comparingInt(BankingDTO.CategoryItem::percentage).reversed())
                    .toList();

            // ── Income by category ───────────────────────────────────────────
            var totalIncome = coalesce(transactionRepository
                    .sumAmountInByUserAndDateRange(userId, range.start(), range.end(), TransactionDirection.IN));

            var incomeRows = transactionRepository
                    .sumInByCategoryAndDateRange(userId, range.start(), range.end(), TransactionDirection.IN);

            var incomeItems = incomeRows.stream()
                    .map(row -> {
                        String name  = row[0] != null ? (String) row[0] : "Khác";
                        String color = row[1] != null ? (String) row[1] : "#4CAF50";
                        var amt      = (BigDecimal) row[2];
                        int pct      = computeUsagePercent(amt, totalIncome);
                        return new BankingDTO.CategoryItem(name, color, amt, pct);
                    })
                    .sorted(Comparator.comparingInt(BankingDTO.CategoryItem::percentage).reversed())
                    .toList();

            return new BankingDTO.CategoryReportResponse(
                    year, totalExpense, totalIncome, expenseItems, incomeItems);

        } catch (Exception ex) {
            log.error("category_report_failed userId={} year={} error={}",
                    userId, year, ex.getMessage(), ex);
            throw new BankingException("CATEGORY_REPORT_ERROR",
                    "Không thể lấy báo cáo danh mục", ex);
        }
    }

    @Transactional(readOnly = true)
    public BankingDTO.MonthlyCategoryResponse getMonthlyCategoryReport(
            Long userId, int year, int month) {
        try {
            var range = DateRange.ofMonth(year, month);

            var totalExpense = coalesce(
                    transactionRepository.sumAmountOutByUserAndDateRange(
                            userId, range.start(), range.end(), TransactionDirection.OUT));

            var rows = transactionRepository
                    .sumOutByCategoryAndDateRange(userId, range.start(), range.end(), TransactionDirection.OUT);

            var items = rows.stream()
                    .map(row -> {
                        String name  = row[0] != null ? (String)  row[0] : "Khác";
                        String color = row[1] != null ? (String)  row[1] : "#999999";
                        var    amt   = (BigDecimal) row[2];
                        int    cnt   = ((Number)    row[3]).intValue();
                        int    pct   = computeUsagePercent(amt, totalExpense);
                        return new BankingDTO.MonthlyCategoryResponse.CategorySlice(
                                name, color, amt, pct, cnt);
                    })
                    .sorted(Comparator
                            .comparingInt(BankingDTO.MonthlyCategoryResponse.CategorySlice::percentage)
                            .reversed())
                    .toList();

            String label = "Tháng %d/%d".formatted(month, year);

            log.info("monthly_category_report userId={} year={} month={} categories={}",
                    userId, year, month, items.size());

            return new BankingDTO.MonthlyCategoryResponse(
                    year, month, label, totalExpense, items);

        } catch (Exception ex) {
            log.error("monthly_category_report_failed userId={} year={} month={} error={}",
                    userId, year, month, ex.getMessage(), ex);
            throw new BankingException("MONTHLY_CATEGORY_REPORT_ERROR",
                    "Không thể lấy báo cáo danh mục tháng", ex);
        }
    }

    @Transactional(readOnly = true)
    public BankingDTO.BudgetSummaryResponse getBudgetSummary(
            Long userId, int year, int month) {
        try {
            // ── 1. fetch all category budgets ────────────────────────────────
            List<BankingDTO.BudgetStatusResponse> categories =
                    getBudgets(userId, year, month); // existing method

            // ── 2. aggregate ─────────────────────────────────────────────────
            BigDecimal totalLimit = categories.stream()
                    .map(BankingDTO.BudgetStatusResponse::limitAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalSpent = categories.stream()
                    .map(BankingDTO.BudgetStatusResponse::spentAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalRemaining = totalLimit.subtract(totalSpent);

            long overBudgetCount = categories.stream()
                    .filter(c -> c.spentAmount().compareTo(c.limitAmount()) > 0)
                    .count();

            int overallUsagePercent = computeUsagePercent(totalSpent, totalLimit);

            String label = "Tháng %d/%d".formatted(month, year);

            log.info("budget_summary userId={} year={} month={} categories={} usage={}%",
                    userId, year, month, categories.size(), overallUsagePercent);

            return new BankingDTO.BudgetSummaryResponse(
                    year,
                    month,
                    label,
                    totalLimit,
                    totalSpent,
                    totalRemaining,
                    (int) overBudgetCount,
                    categories.size(),
                    overallUsagePercent,
                    categories);

        } catch (BankingException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("budget_summary_failed userId={} year={} month={} error={}",
                    userId, year, month, ex.getMessage(), ex);
            throw new BankingException("BUDGET_SUMMARY_ERROR",
                    "Không thể lấy tổng hợp ngân sách", ex);
        }
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
                userId, range.start(), range.end(), TransactionDirection.OUT))
                : coalesce(transactionRepository.sumAmountOutByUserAndCategoryAndDateRange(
                userId, budget.getCategory().getId(), range.start(), range.end(), TransactionDirection.OUT));

        int usagePercent = computeUsagePercent(spent, budget.getLimitAmount());
        boolean alert    = usagePercent >= budget.getAlertThreshold();

        if (alert) {
            log.warn("budget_alert_triggered userId={} budgetId={} usage={}%",
                    userId, budget.getId(), usagePercent);
            kafkaProducerService.sendBudgetAlert(userId,
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
                .sumOutByCategoryAndDateRange(userId, range.start(), range.end(), TransactionDirection.OUT)
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

           static DateRange ofYear(int year) {
               var start = LocalDateTime.of(year, 1, 1, 0, 0);
               return new DateRange(start, LocalDateTime.of(year + 1, 1, 1, 0, 0));
           }
    }
}
