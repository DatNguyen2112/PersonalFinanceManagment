package BankingSystem.Services.ImplService;

import BankingSystem.DTO.BankingDTO;
import BankingSystem.Entity.SepayAccount.SepayAccount;

import java.util.List;

public interface ImplSepayBankAccountService {
    BankingDTO.BankAccountResponse addAccount(
            Long userId, BankingDTO.AddBankAccountRequest req);

    List<BankingDTO.BankAccountResponse> getAccounts(Long userId);

    SepayAccount getAccountForUser(Long accountId, Long userId);
}
