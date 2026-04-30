package BankingSystem.Controller;

import BankingSystem.DTO.BankingDTO;
import BankingSystem.JWTConfig.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/finance")
@RequiredArgsConstructor
@Tag(name = "Personal Finance", description = "Quản lý tài chính cá nhân")
public class PersonalFinanceController {

    private final PersonalFinanceService financeService;

    // ----- Category -----
    @PostMapping("/categories")
    @Operation(summary = "Tạo danh mục chi tiêu tùy chỉnh", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<BankingDTO.CategoryResponse> createCategory(
            @Valid @RequestBody BankingDTO.CategoryRequest request,
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(financeService.createCategory(userDetails.getUserId(), request));
    }

    @GetMapping("/categories")
    @Operation(summary = "Lấy danh sách category (system + custom)", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<List<BankingDTO.CategoryResponse>> getCategories(
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails userDetails) {
        return ResponseEntity.ok(financeService.getCategories(userDetails.getUserId()));
    }

    // ----- Budget -----
    @PostMapping("/budgets")
    @Operation(summary = "Tạo ngân sách", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<BankingDTO.BudgetResponse> createBudget(
            @Valid @RequestBody BankingDTO.BudgetRequest request,
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(financeService.createBudget(userDetails.getUserId(), request));
    }

    @GetMapping("/budgets")
    @Operation(summary = "Lấy danh sách ngân sách theo tháng", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<List<BankingDTO.BudgetResponse>> getBudgets(
            @RequestParam int year, @RequestParam int month,
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails userDetails) {
        return ResponseEntity.ok(financeService.getBudgets(userDetails.getUserId(), year, month));
    }

    // ----- Expense -----
    @PostMapping("/expenses")
    @Operation(summary = "Thêm chi tiêu thủ công", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<BankingDTO.ExpenseResponse> createExpense(
            @Valid @RequestBody BankingDTO.ExpenseRequest request,
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(financeService.createExpense(userDetails.getUserId(), request));
    }

    @GetMapping("/expenses")
    @Operation(summary = "Danh sách chi tiêu (phân trang)", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<Page<BankingDTO.ExpenseResponse>> getExpenses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails userDetails) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("expenseDate").descending());
        return ResponseEntity.ok(financeService.getExpenses(userDetails.getUserId(), pageable));
    }

    // ----- Savings Goal -----
    @PostMapping("/goals")
    @Operation(summary = "Tạo mục tiêu tiết kiệm", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<BankingDTO.SavingsGoalResponse> createGoal(
            @Valid @RequestBody BankingDTO.SavingsGoalRequest request,
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(financeService.createSavingsGoal(userDetails.getUserId(), request));
    }

    @GetMapping("/goals")
    @Operation(summary = "Lấy danh sách mục tiêu tiết kiệm", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<List<BankingDTO.SavingsGoalResponse>> getSavingsGoals(
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails userDetails) {
        return ResponseEntity.ok(financeService.getSavingsGoals(userDetails.getUserId()));
    }

    @PostMapping("/goals/{goalId}/contribute")
    @Operation(summary = "Nạp tiền vào mục tiêu tiết kiệm", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<BankingDTO.SavingsGoalResponse> contribute(
            @PathVariable Long goalId,
            @RequestParam @Positive BigDecimal amount,
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails userDetails) {
        return ResponseEntity.ok(financeService.contributeToGoal(userDetails.getUserId(), goalId, amount));
    }

    // ----- Report -----
    @GetMapping("/reports/monthly")
    @Operation(summary = "Báo cáo tài chính tháng", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<BankingDTO.FinancialReportResponse> getMonthlyReport(
            @RequestParam int year, @RequestParam int month,
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails userDetails) {
        return ResponseEntity.ok(financeService.getMonthlyReport(userDetails.getUserId(), year, month));
    }
}

