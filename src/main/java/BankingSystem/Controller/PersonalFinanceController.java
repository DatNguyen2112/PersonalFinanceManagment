package BankingSystem.Controller;

import BankingSystem.DTO.BankingDTO;
import BankingSystem.Entity.SpendingCategory;
import BankingSystem.JWTConfig.UserDetailsImpl;
import BankingSystem.Services.*;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/personal-finance")
@RequiredArgsConstructor
@Slf4j
public class PersonalFinanceController {

    private final SepayBankAccountService bankAccountService;
    private final SepayTransactionSyncService syncService;
    private final PersonalFinanceService financeService;
    private final SpendingCategoryService categoryService;

    @GetMapping("/dashboard")
    @Operation(summary = "Lấy dữ liệu Tổng quan (Dashboard)")
    public ResponseEntity<BankingDTO.DashboardResponse> getDashboard(
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails u) {

        int year  = LocalDate.now().getYear();
        int month = LocalDate.now().getMonthValue();

        // Monthly summary (income, expense, savings)
        var monthly = financeService.getMonthlySummary(u.getUserId(), year, month);

        // Last 6 months cash-flow for chart
        var cashFlow = financeService.getLast6MonthsCashFlow(u.getUserId());

        // Latest 5 transactions
        var recentReq = new BankingDTO.TransactionQueryRequest(
                null, null, null, null, null, 1, 5);
        var recentTx = financeService.getTransactions(u.getUserId(), recentReq).getContent();

        // Top budget categories for the sidebar widget
        var budgets = financeService.getBudgets(u.getUserId(), year, month);

        // Expense categories with highest spending this month (for budget alerts)
        var expenses = financeService.getMonthlyCategoryReport(u.getUserId(), year, month);


        var response = new BankingDTO.DashboardResponse(
                monthly,
                expenses,
                cashFlow,
                recentTx,
                budgets);

        return ResponseEntity.ok(response);
    }

    // ── Tài khoản ngân hàng ────────────────────────────────────────────────

    @PostMapping("/bank-accounts")
    public ResponseEntity<BankingDTO.BankAccountResponse> addAccount(
            @Valid @RequestBody BankingDTO.AddBankAccountRequest req,
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails u) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bankAccountService.addAccount(u.getUserId(), req));
    }

    @GetMapping("/bank-accounts")
    public ResponseEntity<List<BankingDTO.BankAccountResponse>> getAccounts(
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails u) {
        var result = bankAccountService.getAccounts(u.getUserId());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/bank-accounts/{id}/sync")
    public ResponseEntity<Map<String, Object>> triggerSync(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails u) {
        var account = bankAccountService.getAccountForUser(id, u.getUserId());
        syncService.syncAccount(account);
        return ResponseEntity.ok(Map.of("message", "Sync triggered"));
    }

    // ── Giao dịch ──────────────────────────────────────────────────────────

    @GetMapping("/transactions")
    public ResponseEntity<Page<BankingDTO.TransactionResponse>> getTransactions(
            @Valid BankingDTO.TransactionQueryRequest req,
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails u) {
        return ResponseEntity.ok(
                financeService.getTransactions(u.getUserId(), req));
    }

    @PatchMapping("/transactions/{id}/category")
    public ResponseEntity<BankingDTO.TransactionResponse> updateCategory(
            @PathVariable Long id,
            @RequestParam Long categoryId,
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails u) {
        return ResponseEntity.ok(
                financeService.updateCategory(id, categoryId, u.getUserId()));
    }

    // ── Danh mục ───────────────────────────────────────────────────────────

    @GetMapping("/categories")
    public ResponseEntity<List<SpendingCategory>> getCategories(
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails u) {
        return ResponseEntity.ok(categoryService.getCategories(u.getUserId()));
    }

    // ── Ngân sách ──────────────────────────────────────────────────────────

    @PostMapping("/budgets")
    public ResponseEntity<BankingDTO.BudgetStatusResponse> createBudget(
            @Valid @RequestBody BankingDTO.BudgetRequest req,
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails u) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(financeService.createOrUpdateBudget(u.getUserId(), req));
    }

    @GetMapping("/budgets")
    @Operation(summary = "Danh sách ngân sách tháng")
    public ResponseEntity<BankingDTO.BudgetListResponse> getBudgets(
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails u,
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") int year,
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().monthValue}") int month) {

        var items  = financeService.getBudgets(u.getUserId(), year, month);
        var total  = financeService.getBudgetStatus(u.getUserId(), null, year, month);  // null categoryId = total

        var response = new BankingDTO.BudgetListResponse(
                year, month, total, items,
                (int) items.stream().filter(BankingDTO.BudgetStatusResponse::alertTriggered).count(),
                items.size());

        return ResponseEntity.ok(response);
    }

    // ── Báo cáo ────────────────────────────────────────────────────────────

    @GetMapping("/reports/summary")
    @Operation(summary = "Báo cáo tổng quan năm")
    public ResponseEntity<BankingDTO.YearlySummaryResponse> getYearlySummary(
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails u,
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") int year) {

        return ResponseEntity.ok(
                financeService.getYearlySummary(u.getUserId(), year));
    }

    @GetMapping("/reports/categories")
    @Operation(summary = "Báo cáo chi tiêu và thu nhập theo danh mục")
    public ResponseEntity<BankingDTO.CategoryReportResponse> getCategoryReport(
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails u,
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") int year) {

        return ResponseEntity.ok(
                financeService.getCategoryReport(u.getUserId(), year));
    }
}

