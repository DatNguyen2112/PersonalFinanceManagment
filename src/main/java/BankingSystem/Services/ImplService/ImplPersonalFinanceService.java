package BankingSystem.Services.ImplService;

import BankingSystem.DTO.BankingDTO;
import org.springframework.data.domain.Page;

import java.util.List;

public interface ImplPersonalFinanceService {
    BankingDTO.BudgetStatusResponse createOrUpdateBudget(
            Long userId, BankingDTO.BudgetRequest req);

    List<BankingDTO.BudgetStatusResponse> getBudgets(
            Long userId, int year, int month);

    BankingDTO.MonthlySummaryResponse getMonthlySummary(
            Long userId, int year, int month);

    Page<BankingDTO.TransactionResponse> getTransactions(
            Long userId, BankingDTO.TransactionQueryRequest req);

    BankingDTO.TransactionResponse updateCategory(
            Long transactionId, Long categoryId, Long userId);


}
