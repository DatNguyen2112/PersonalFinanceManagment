package BankingSystem.Config.Sepay;

import jakarta.validation.constraints.Null;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "sepay.bank-hub")
@Validated
public record SepayBankHubProperties(
        @Null String baseUrl,
        @Null String clientId,
        @Null String clientSecret,
        @Null String companyXid,
        @Null String webhookSecret,
        @Null String completionRedirectUri
) {}
