package BankingSystem.Services.ImplService;
import BankingSystem.DTO.BankingDTO;

public interface ImplAuthService {
    BankingDTO.AuthResponse register(BankingDTO.RegisterRequest request);
    BankingDTO.AuthResponse login(BankingDTO.LoginRequest request);
}
