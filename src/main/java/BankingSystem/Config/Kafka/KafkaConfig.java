package BankingSystem.Config.Kafka;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
@RequiredArgsConstructor
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // ── Producer ───────────────────────────────────────────────────────────

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,  bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                JacksonJsonSerializer.class);                    // ← mới
        config.put(ProducerConfig.ACKS_CONFIG,               "all");
        config.put(ProducerConfig.RETRIES_CONFIG,            3);
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // ── Consumer ───────────────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,  bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG,           "banking-group");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,  "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                JacksonJsonDeserializer.class);                  // ← mới
        config.put(JacksonJsonDeserializer.TRUSTED_PACKAGES, "BankingSystem.*");
        config.put(JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        config.put(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE, "java.util.Map");
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object>
    kafkaListenerContainerFactory() {

        var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        factory.setConsumerFactory(consumerFactory());

        // Manual ACK — chỉ commit khi xử lý thành công
        factory.getContainerProperties()
                .setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // Retry 3 lần trước khi đẩy sang DLT
        factory.setCommonErrorHandler(defaultErrorHandler());

        return factory;
    }

    @Bean
    public DefaultErrorHandler defaultErrorHandler() {
        var backOff = new FixedBackOff(1000L, 3L);
        var handler = new DefaultErrorHandler(
                (record, ex) -> log.error(
                        "kafka_message_failed topic={} partition={} offset={} error={}",
                        record.topic(), record.partition(),
                        record.offset(), ex.getMessage()),
                backOff);

        handler.addNotRetryableExceptions(
                EntityNotFoundException.class,
                IllegalArgumentException.class
        );

        return handler;
    }

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(KafkaConfig.class);

    // ── Topics ─────────────────────────────────────────────────────────────

    @Bean
    public NewTopic sepayTransactionTopic() {
        return TopicBuilder.name("banking.sepay.transaction")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic sepaySyncTopic() {
        return TopicBuilder.name("banking.sepay.sync")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic sepayBudgetAlertTopic() {
        return TopicBuilder.name("banking.sepay.budget-alert")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic sepayBankHubTopic() {
        return TopicBuilder.name("banking.sepay.bankhub")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
