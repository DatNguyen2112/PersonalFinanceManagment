package BankingSystem.Services;

import BankingSystem.DTO.BankingDTO;
import BankingSystem.Entity.SepayAccount.SepayAccount;
import BankingSystem.Entity.SepayAccount.SepayAccountStatus;
import BankingSystem.Exception.BankAccountAlreadyExistsException;
import BankingSystem.Exception.BankAccountNotFoundException;
import BankingSystem.Exception.BankingException;
import BankingSystem.Repositories.SepayBankAccountRepository;
import BankingSystem.Repositories.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class SepayBankAccountService {

    private final SepayBankAccountRepository bankAccountRepository;
    private final UserRepository userRepository;

    // ── Add account ────────────────────────────────────────────────────────

    @Transactional
    public BankingDTO.BankAccountResponse addAccount(
            Long userId, BankingDTO.AddBankAccountRequest req) {
        try {
            if (bankAccountRepository.existsByAccountNumber(req.accountNumber())) {
                throw new BankAccountAlreadyExistsException(req.accountNumber());
            }

            var user = userRepository.findById(userId)
                    .orElseThrow(() -> new EntityNotFoundException(
                            "User not found id=" + userId));

            var account = SepayAccount.builder()
                    .user(user)
                    .accountNumber(req.accountNumber())
                    .bankBrandName(req.bankBrandName())
                    .displayName(req.displayName() != null
                            ? req.displayName()
                            : req.bankBrandName() + " - " + maskAccountNumber(req.accountNumber()))
                    .status(SepayAccountStatus.ACTIVE)
                    .build();

            bankAccountRepository.save(account);

            log.info("sepay_account_added userId={} account={} bank={}",
                    userId, maskAccountNumber(req.accountNumber()), req.bankBrandName());

            return mapToResponse(account);

        } catch (BankingException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("sepay_account_add_failed userId={} error={}", userId, ex.getMessage(), ex);
            throw new BankingException("ACCOUNT_ADD_ERROR",
                    "Không thể thêm tài khoản ngân hàng", ex);
        }
    }

    // ── Get list ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BankingDTO.BankAccountResponse> getAccounts(Long userId) {
        return bankAccountRepository.findByUserId(userId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // ── Get single — dùng cho sync, verify ownership ───────────────────────

    public SepayAccount getAccountForUser(Long accountId, Long userId) {
        return bankAccountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new BankAccountNotFoundException(
                        "id=" + accountId + " userId=" + userId));
    }

    // ── Update display name ────────────────────────────────────────────────

    @Transactional
    public BankingDTO.BankAccountResponse updateDisplayName(
            Long accountId, Long userId, String displayName) {

        var account = getAccountForUser(accountId, userId);
        account.setDisplayName(displayName);
        bankAccountRepository.save(account);

        log.info("sepay_account_display_name_updated accountId={}", accountId);

        return mapToResponse(account);
    }

    // ── Pause / Resume ─────────────────────────────────────────────────────

    @Transactional
    public BankingDTO.BankAccountResponse pauseAccount(Long accountId, Long userId) {
        return changeStatus(accountId, userId, SepayAccountStatus.PAUSED);
    }

    @Transactional
    public BankingDTO.BankAccountResponse resumeAccount(Long accountId, Long userId) {
        return changeStatus(accountId, userId, SepayAccountStatus.ACTIVE);
    }

    // ── Remove ─────────────────────────────────────────────────────────────

    @Transactional
    public void removeAccount(Long accountId, Long userId) {
        var account = getAccountForUser(accountId, userId);
        account.setStatus(SepayAccountStatus.REMOVED);
        bankAccountRepository.save(account);

        log.info("sepay_account_removed accountId={} userId={}", accountId, userId);
    }

    // ── Update sync cursor — gọi sau mỗi lần pull thành công ──────────────

    @Transactional
    public void updateSyncCursor(Long accountId, Long lastSyncedTransactionId) {
        var account = bankAccountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Bank account not found id=" + accountId));

        account.setLastSyncedTransactionId(lastSyncedTransactionId);
        account.setLastSyncedAt(LocalDateTime.now());
        bankAccountRepository.save(account);

        log.info("sepay_sync_cursor_updated accountId={} lastTxId={}",
                accountId, lastSyncedTransactionId);
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private BankingDTO.BankAccountResponse changeStatus(
            Long accountId, Long userId, SepayAccountStatus newStatus) {

        var account = getAccountForUser(accountId, userId);

        if (account.getStatus() == SepayAccountStatus.REMOVED) {
            throw new IllegalStateException(
                    "Cannot change status of a removed account id=" + accountId);
        }

        account.setStatus(newStatus);
        bankAccountRepository.save(account);

        log.info("sepay_account_status_changed accountId={} status={}",
                accountId, newStatus);

        return mapToResponse(account);
    }

    private BankingDTO.BankAccountResponse mapToResponse(SepayAccount account) {
        return new BankingDTO.BankAccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getBankBrandName(),
                account.getDisplayName(),
                account.getLastSyncedAt(),
                account.getStatus().name());
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) return "****";
        return "*".repeat(accountNumber.length() - 4)
                + accountNumber.substring(accountNumber.length() - 4);
    }
}
