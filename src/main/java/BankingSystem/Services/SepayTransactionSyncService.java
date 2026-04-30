package BankingSystem.Services;

import BankingSystem.Config.SepayApiClient;
import BankingSystem.DTO.BankingDTO;
import BankingSystem.Entity.SepayAccount.SepayAccount;
import BankingSystem.Entity.SepayAccount.SepayAccountStatus;
import BankingSystem.Entity.SepayTransaction.SepayTransaction;
import BankingSystem.Entity.SepayTransaction.SyncSourceType;
import BankingSystem.Entity.SepayTransaction.TransactionDirection;
import BankingSystem.Exception.SepayApiExecption;
import BankingSystem.Repositories.SepayBankAccountRepository;
import BankingSystem.Repositories.SepayTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
@RequiredArgsConstructor
public class SepayTransactionSyncService {

    private final SepayApiClient sepayApiClient;
    private final SepayBankAccountRepository bankAccountRepository;
    private final SepayTransactionRepository transactionRepository;
    private final SpendingCategoryService categoryService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(cron = "${sepay.api.sync.cron}")
    public void scheduledSync() {
        log.info("sepay_scheduled_sync_start");
        bankAccountRepository.findByStatus(SepayAccountStatus.ACTIVE)
                .forEach(this::syncAccount);
        log.info("sepay_scheduled_sync_done");
    }

    public void syncAccount(SepayAccount account) {
        try {
            var req = new BankingDTO.SepayListRequest(
                    account.getAccountNumber(),
                    account.getLastSyncedTransactionId(),  // incremental via since_id
                    100,
                    null, null
            );

            var response = sepayApiClient.listTransactions(req);
            if (response == null || response.transactions() == null
                    || response.transactions().isEmpty()) {
                return;
            }

            var newTxs = response.transactions().stream()
                    .filter(item -> !transactionRepository.existsBySepayId(item.id()))
                    .map(item -> mapToEntity(item, account))
                    .toList();

            transactionRepository.saveAll(newTxs);

            // Cập nhật con trỏ incremental
            response.transactions().stream()
                    .mapToLong(BankingDTO.SepayTransactionItem::id)
                    .max()
                    .ifPresent(maxId -> {
                        account.setLastSyncedTransactionId(maxId);
                        account.setLastSyncedAt(LocalDateTime.now());
                        bankAccountRepository.save(account);
                    });

            log.info("sepay_sync_done account={} new_tx_count={}",
                    account.getAccountNumber(), newTxs.size());

            // Kafka event cho downstream (notification, budget check…)
//            if (!newTxs.isEmpty()) {
//                kafkaTemplate.send("banking.sepay.sync",
//                        account.getUser().getId().toString(),
//                        new SepaySyncEvent(account.getId(), newTxs.size()));
//            }

        } catch (SepayApiExecption ex) {
            log.error("sepay_sync_failed account={}", account.getAccountNumber(), ex);
        }
    }

    private SepayTransaction mapToEntity(BankingDTO.SepayTransactionItem item,
                                         SepayAccount account) {
        var direction = item.amountIn().compareTo(BigDecimal.ZERO) > 0
                ? TransactionDirection.IN
                : TransactionDirection.OUT;

        var category = categoryService.autoClassify(item.transactionContent());

        return SepayTransaction.builder()
                .sepayId(item.id())
                .user(account.getUser())
                .sepayBankAccount(account)
                .accountNumber(item.accountNumber())
                .bankBrandName(item.bankBrandName())
                .transactionDate(parseDate(item.transactionDate()))
                .amountIn(item.amountIn())
                .amountOut(item.amountOut())
                .accumulated(item.accumulated())
                .transactionContent(item.transactionContent())
                .referenceNumber(item.referenceNumber())
                .code(item.code())
                .subAccount(item.subAccount())
                .direction(direction)
                .category(category)
                .source(SyncSourceType.PULL_API)
                .build();
    }

    private LocalDateTime parseDate(String raw) {
        return LocalDateTime.parse(raw,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
