package BankingSystem.Services;

import BankingSystem.Config.Kafka.KafkaEventConfig;
import BankingSystem.DTO.BankingDTO;
import BankingSystem.Entity.SepayTransaction.SepayTransaction;
import BankingSystem.Entity.SepayTransaction.SyncSourceType;
import BankingSystem.Entity.SepayTransaction.TransactionDirection;
import BankingSystem.Exception.BankAccountNotFoundException;
import BankingSystem.Exception.BankingException;
import BankingSystem.Repositories.SepayBankAccountRepository;
import BankingSystem.Repositories.SepayTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

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
        try {
            if (transactionRepository.existsByReferenceNumber(payload.referenceCode())) {
                log.info("sepay_webhook_duplicate ref={}", payload.referenceCode());
                return;
            }

            var account = bankAccountRepository
                    .findByAccountNumber(payload.accountNumber())
                    .orElseThrow(() -> new BankAccountNotFoundException(
                            payload.accountNumber()));

            var direction = payload.transferType() != null && payload.transferType().equalsIgnoreCase("IN")
                    ? TransactionDirection.IN : TransactionDirection.OUT;

            var category = categoryService.autoClassify(payload.content());

            var tx = SepayTransaction.builder()
                    .sepayId(payload.id())
                    .user(account.getUser())
                    .sepayBankAccount(account)
                    .accountNumber(payload.accountNumber())
                    .bankBrandName(payload.gateway())
                    .transactionDate(parseDate(payload.transactionDate()))
                    .amountIn(coalesce(payload.transferAmount()))
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

            log.info("sepay_webhook_saved account={} direction={} amount={} category={}",
                    payload.accountNumber(), direction,
                    payload.transferAmount(), category);

            kafkaProducerService.sendSepayTransaction(
                    account.getUser().getId(),
                    new KafkaEventConfig.SepayTransactionEvent(tx.getId(), direction.name()));

        } catch (BankAccountNotFoundException ex) {
            log.warn("sepay_webhook_unknown_account account={}", payload.accountNumber());
        } catch (DataIntegrityViolationException ex) {
            log.warn("sepay_webhook_duplicate_skipped ref={}",
                    payload.referenceCode());
        } catch (DateTimeParseException ex) {
            log.error("sepay_webhook_invalid_date date={} error={}",
                    payload.transactionDate(), ex.getMessage());
            throw new BankingException("INVALID_DATE_FORMAT",
                    "Invalid transaction date: " + payload.transactionDate(), ex);
        } catch (Exception ex) {
            log.error("sepay_webhook_process_failed ref={} error={}",
                    payload.referenceCode(), ex.getMessage(), ex);
            throw ex;
        }
    }

    private LocalDateTime parseDate(String raw) {
        return LocalDateTime.parse(raw,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private BigDecimal coalesce(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
