package BankingSystem.Exception;

public class BudgetNotFoundException extends BankingException {
    public BudgetNotFoundException(Long categoryId, int year, int month) {
        super("BUDGET_NOT_FOUND",
                "Budget not found categoryId=%s year=%d month=%d"
                        .formatted(categoryId, year, month));
    }
}
