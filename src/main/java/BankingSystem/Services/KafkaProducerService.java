package BankingSystem.Services;

import BankingSystem.Config.Kafka.KafkaEventConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    // ── Inject NewTopic beans từ KafkaConfig ──────────────────────────────
    private final NewTopic sepayTransactionTopic;
    private final NewTopic sepaySyncTopic;
    private final NewTopic sepayBudgetAlertTopic;
    private final NewTopic sepayBankHubTopic;

    // ── Generic send — dùng cho mọi topic ─────────────────────────────────

    public void send(NewTopic topic, String key, Object payload) {
        kafkaTemplate.send(topic.name(), key, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("kafka_send_failed topic={} key={} error={}",
                                topic.name(), key, ex.getMessage(), ex);
                    } else {
                        log.debug("kafka_send_ok topic={} key={} partition={} offset={}",
                                topic.name(), key,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    // ── Domain-specific methods ────────────────────────────────────────────

    public void sendSepayTransaction(Long userId, KafkaEventConfig.SepayTransactionEvent event) {
        send(sepayTransactionTopic, userId.toString(), event);
    }

    public void sendSepaySync(Long bankAccountId, KafkaEventConfig.SepaySyncEvent event) {
        send(sepaySyncTopic, bankAccountId.toString(), event);
    }

    public void sendBudgetAlert(Long userId, KafkaEventConfig.BudgetAlertEvent event) {
        send(sepayBudgetAlertTopic, userId.toString(), event);
    }

    public void sendBankHubEvent(Long userId, Object event) {
        send(sepayBankHubTopic, userId.toString(), event);
    }
}
