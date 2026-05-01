package BankingSystem.Services;

import BankingSystem.Config.Kafka.KafkaEventConfig;
import BankingSystem.DTO.BankingDTO;
import BankingSystem.Entity.SepayTransaction.SepayTransaction;
import BankingSystem.Entity.SepayTransaction.SyncSourceType;
import BankingSystem.Entity.SepayTransaction.TransactionDirection;
import BankingSystem.Repositories.SepayBankAccountRepository;
import BankingSystem.Repositories.SepayTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
@RequiredArgsConstructor
public class SepayWebhookService {

    private final SepayTransactionRepository transactionRepository;
    private final SepayBankAccountRepository bankAccountRepository;
    private final SpendingCategoryService categoryService;
    private final KafkaProducerService kafkaProducerService;

    @Transactional
    public void process(BankingDTO.SepayWebhookPayload payload) {
        if (transactionRepository.existsByReferenceNumber(payload.referenceCode())) {
            log.info("sepay_webhook_duplicate ref={}", payload.referenceCode());
            return;
        }

        var account = bankAccountRepository
                .findByAccountNumber(payload.accountNumber())
                .orElseGet(() -> {
                    // Tài khoản chưa được đăng ký trong hệ thống — bỏ qua
                    log.warn("sepay_webhook_unknown_account account={}",
                            payload.accountNumber());
                    return null;
                });

        if (account == null) return;

        var direction = payload.amountIn() != null
                && payload.amountIn().compareTo(BigDecimal.ZERO) > 0
                ? TransactionDirection.IN : TransactionDirection.OUT;

        var category = categoryService.autoClassify(payload.content());

        var tx = SepayTransaction.builder()
                .sepayId(payload.sepayId())
                .user(account.getUser())
                .sepayBankAccount(account)
                .accountNumber(payload.accountNumber())
                .bankBrandName(payload.gateway())
                .transactionDate(parseDate(payload.transactionDate()))
                .amountIn(coalesce(payload.amountIn()))
                .amountOut(coalesce(payload.amountOut()))
                .accumulated(coalesce(payload.accumulated()))
                .transactionContent(payload.content())
                .referenceNumber(payload.referenceCode())
                .code(payload.code())
                .subAccount(payload.subAccount())
                .direction(direction)
                .category(category)
                .source(SyncSourceType.WEBHOOK)
                .build();

        transactionRepository.save(tx);
        log.info("sepay_webhook_saved account={} direction={} amount={}",
                payload.accountNumber(), direction,
                direction == TransactionDirection.IN ? payload.amountIn() : payload.amountOut());

        kafkaProducerService.sendSepayTransaction(
                account.getUser().getId(),
                new KafkaEventConfig.SepayTransactionEvent(tx.getId(), direction.name()));
    }

    private LocalDateTime parseDate(String raw) {
        return LocalDateTime.parse(raw,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private BigDecimal coalesce(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
