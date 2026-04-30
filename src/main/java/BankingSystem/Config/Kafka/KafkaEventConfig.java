package BankingSystem.Config.Kafka;

public class KafkaEventConfig {
    public record SepayTransactionEvent(Long transactionId, String direction) {}
    public record SepaySyncEvent(Long bankAccountId, int newTransactionCount) {}
    public record BudgetAlertEvent(Long budgetId, int usagePercent) {}
}
