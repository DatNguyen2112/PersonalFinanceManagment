package BankingSystem.Services;

import BankingSystem.Config.Kafka.KafkaEventConfig;
import BankingSystem.Config.Sepay.SepayBankHubClient;
import BankingSystem.DTO.BankingDTO;
import BankingSystem.DTO.SepayBankHub;
import BankingSystem.Entity.SepayAccount.SepayAccount;
import BankingSystem.Entity.SepayAccount.SepayAccountStatus;
import BankingSystem.Exception.BankHubException;
import BankingSystem.Exception.BankingException;
import BankingSystem.Repositories.SepayBankAccountRepository;
import BankingSystem.Repositories.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class SepayBankHubService {

    private final SepayBankHubClient bankHubClient;
    private final SepayBankAccountRepository bankAccountRepository;
    private final UserRepository userRepository;

    // ── Khởi tạo liên kết — trả về hosted link cho frontend mở iframe ─────

    @Transactional(readOnly = true)
    public SepayBankHub.BankHubInitResponse initLinkAccount(Long userId) {
        try {
            userRepository.findById(userId)
                    .orElseThrow(() -> new EntityNotFoundException(
                            "User not found id=" + userId));

            var response = bankHubClient.createLinkToken(
                    "LINK_BANK_ACCOUNT", null, null);

            log.info("bank_hub_init_link userId={} linkTokenXid={}",
                    userId, response.xid());

            return new SepayBankHub.BankHubInitResponse(
                    response.xid(), response.hostedLinkUrl(), response.expiresAt());

        } catch (BankingException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("bank_hub_init_link_failed userId={} error={}",
                    userId, ex.getMessage(), ex);
            throw new BankHubException("Không thể khởi tạo liên kết ngân hàng", ex);
        }
    }

    // ── Khởi tạo hủy liên kết ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SepayBankHub.BankHubInitResponse initUnlinkAccount(
            Long userId, String bankAccountXid) {

        // Verify ownership — tài khoản phải thuộc user này
        bankAccountRepository.findByBankHubXidAndUserId(bankAccountXid, userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Bank account not found xid=" + bankAccountXid));

        var response = bankHubClient.createLinkToken(
                "UNLINK_BANK_ACCOUNT", bankAccountXid, null);

        log.info("bank_hub_init_unlink userId={} bankAccountXid={} linkTokenXid={}",
                userId, bankAccountXid, response.xid());

        return new SepayBankHub.BankHubInitResponse(
                response.xid(),
                response.hostedLinkUrl(),
                response.expiresAt());
    }

    // ── Xử lý webhook từ SePay Bank Hub ───────────────────────────────────

    @Transactional
    public void processWebhookEvent(SepayBankHub.BankHubWebhookPayload payload) {
        try {
            log.info("bank_hub_webhook_event event={} xid={}",
                    payload.event(), payload.xid());

            switch (payload.event()) {
                case "BANK_ACCOUNT_LINKED"   -> handleAccountLinked(payload.metadata());
                case "BANK_ACCOUNT_UNLINKED" -> handleAccountUnlinked(payload.metadata());
                case "LINK_SESSION_STATE_CHANGED" ->
                        log.info("bank_hub_session_state state={}",
                                payload.metadata().state());
                default ->
                        log.debug("bank_hub_webhook_ignored event={}", payload.event());
            }
        } catch (BankingException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("bank_hub_webhook_failed event={} xid={} error={}",
                    payload.event(), payload.xid(), ex.getMessage(), ex);
            // re-throw để SePay nhận HTTP 500 và retry
            throw new BankHubException("Webhook processing failed", ex);
        }
    }

    private void handleAccountLinked(SepayBankHub.BankHubWebhookMetadata meta) {
        if (meta.bankAccountXid() == null) {
            log.warn("bank_hub_linked_missing_xid");
            return;
        }

        // Tránh duplicate
        if (bankAccountRepository.existsByBankHubXid(meta.bankAccountXid())) {
            log.info("bank_hub_linked_already_exists xid={}", meta.bankAccountXid());
            return;
        }

        // Gọi lại Bank Hub API lấy full thông tin tài khoản
        var accountDetail = bankHubClient.getBankAccount(meta.bankAccountXid());

        // Lưu vào DB — chưa gắn userId (cần user tự confirm hoặc map qua session)
        // Trong thực tế cần lưu userId vào link token metadata hoặc dùng state parameter
        var account = SepayAccount.builder()
                .bankHubXid(accountDetail.xid())
                .accountNumber(accountDetail.accountNumber())
                .bankBrandName(accountDetail.brandName())
                .accountHolderName(accountDetail.accountHolderName())
                .displayName(accountDetail.brandName() + " - "
                        + maskAccount(accountDetail.accountNumber()))
                .status(SepayAccountStatus.ACTIVE)
                .build();

        bankAccountRepository.save(account);

        log.info("bank_hub_account_linked xid={} account={}",
                meta.bankAccountXid(), maskAccount(accountDetail.accountNumber()));
    }

    private void handleAccountUnlinked(SepayBankHub.BankHubWebhookMetadata meta) {
        if (meta.bankAccountXid() == null) return;

        bankAccountRepository.findByBankHubXid(meta.bankAccountXid())
                .ifPresentOrElse(account -> {
                            account.setStatus(SepayAccountStatus.REMOVED);
                            bankAccountRepository.save(account);
                            log.info("bank_hub_account_unlinked xid={}", meta.bankAccountXid());
                        }, () ->
                                log.warn("bank_hub_unlinked_not_found xid={}", meta.bankAccountXid())
                );
    }

    // ── Sync tài khoản từ Bank Hub về DB (dùng cho reconcile) ─────────────

    @Transactional
    public int syncLinkedAccountsFromBankHub(Long userId) {
        var remoteAccounts = bankHubClient.listLinkedAccounts();
        int synced = 0;

        for (var item : remoteAccounts) {
            if (bankAccountRepository.existsByBankHubXid(item.xid())) continue;

            var user = userRepository.findById(userId).orElse(null);
            var account = SepayAccount.builder()
                    .user(user)
                    .bankHubXid(item.xid())
                    .accountNumber(item.accountNumber())
                    .bankBrandName(item.brandName())
                    .accountHolderName(item.accountHolderName())
                    .displayName(item.brandName() + " - " + maskAccount(item.accountNumber()))
                    .status(SepayAccountStatus.ACTIVE)
                    .build();

            bankAccountRepository.save(account);
            synced++;
        }

        log.info("bank_hub_sync_done userId={} synced={}", userId, synced);
        return synced;
    }

    private String maskAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) return "****";
        return "*".repeat(accountNumber.length() - 4)
                + accountNumber.substring(accountNumber.length() - 4);
    }
}
