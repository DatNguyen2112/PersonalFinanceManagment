package BankingSystem.Repositories;

import BankingSystem.Entity.SepayAccount.SepayAccount;
import BankingSystem.Entity.SepayAccount.SepayAccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SepayBankAccountRepository extends JpaRepository<SepayAccount, Long> {
    SepayAccount findByAccountNumber (String bankAccountNumber);
    List<SepayAccount> findByStatus (SepayAccountStatus bankAccountStatus);
}
