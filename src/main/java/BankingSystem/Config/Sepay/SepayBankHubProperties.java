package BankingSystem.Config.Sepay;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "sepay.bank-hub")
@Validated
public record SepayBankHubProperties(
        @NotBlank String baseUrl,
        @NotBlank String clientId,
        @NotBlank String clientSecret,
        @NotBlank String companyXid,
        @NotBlank String webhookSecret,
        @NotBlank String completionRedirectUri
) {}
