package BankingSystem.Services.ImplService;

import BankingSystem.Config.Kafka.KafkaEventConfig;

public interface ImplKafkaProducerService {
    void sendSepayTransaction(Long userId, KafkaEventConfig.SepayTransactionEvent event);
    void sendSepaySync(Long bankAccountId, KafkaEventConfig.SepaySyncEvent event);
    void sendBudgetAlert(Long userId, KafkaEventConfig.BudgetAlertEvent event);
}
