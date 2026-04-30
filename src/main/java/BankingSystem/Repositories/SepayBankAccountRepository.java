package BankingSystem.Repositories;

import BankingSystem.Entity.SepayAccount.SepayAccount;
import BankingSystem.Entity.SepayAccount.SepayAccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SepayBankAccountRepository extends JpaRepository<SepayAccount, Long> {
    boolean existsByAccountNumber(String accountNumber);

    Optional<SepayAccount> findByAccountNumber(String accountNumber);

    Optional<SepayAccount> findByIdAndUserId(Long id, Long userId);

    List<SepayAccount> findByUserId(Long userId);

    List<SepayAccount> findByStatus(SepayAccountStatus status);
}
