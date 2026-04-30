package BankingSystem.Controller;

import BankingSystem.DTO.BankingDTO;
import BankingSystem.Entity.SpendingCategory;
import BankingSystem.JWTConfig.UserDetailsImpl;
import BankingSystem.Services.PersonalFinanceService;
import BankingSystem.Services.SepayBankAccountService;
import BankingSystem.Services.SepayTransactionSyncService;
import BankingSystem.Services.SpendingCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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
        return ResponseEntity.ok(categoryService.getSystemCategories(u.getUserId()));
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
    public ResponseEntity<List<BankingDTO.BudgetStatusResponse>> getBudgets(
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(defaultValue = "0") int month,
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails u) {
        int y = year  == 0 ? LocalDate.now().getYear()        : year;
        int m = month == 0 ? LocalDate.now().getMonthValue()  : month;
        return ResponseEntity.ok(financeService.getBudgets(u.getUserId(), y, m));
    }

    // ── Báo cáo ────────────────────────────────────────────────────────────

    @GetMapping("/reports/monthly")
    public ResponseEntity<BankingDTO.MonthlySummaryResponse> monthlySummary(
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(defaultValue = "0") int month,
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails u) {
        int y = year  == 0 ? LocalDate.now().getYear()       : year;
        int m = month == 0 ? LocalDate.now().getMonthValue() : month;
        return ResponseEntity.ok(financeService.getMonthlySummary(u.getUserId(), y, m));
    }
}

