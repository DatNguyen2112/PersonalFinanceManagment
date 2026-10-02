package BankingSystem.Services.ImplService;

import BankingSystem.DTO.BankingDTO;

public interface ImplSepayWebhookService {
    void process(BankingDTO.SepayWebhookPayload payload);
}
