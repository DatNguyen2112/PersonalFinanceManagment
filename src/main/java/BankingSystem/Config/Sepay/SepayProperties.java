package BankingSystem.Config.Sepay;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "sepay.api")
@Validated
public record SepayProperties(
        @NotBlank String baseUrl,
        @NotBlank String token,
        @NotBlank String webhookSecret,
        int rateLimitPerSecond,
        SyncConfig sync
) {
    public record SyncConfig(String cron, int pageSize) {}
}
