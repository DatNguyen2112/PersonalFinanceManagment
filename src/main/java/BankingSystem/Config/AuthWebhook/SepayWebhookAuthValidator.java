package BankingSystem.Config.AuthWebhook;

import BankingSystem.Config.Sepay.SepayProperties;
import BankingSystem.Exception.WebhookAuthException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
@Slf4j
@RequiredArgsConstructor
public class SepayWebhookAuthValidator {

    private static final String APIKEY_PREFIX = "Apikey ";

    private final SepayProperties props;

    public void validateUserApiWebhook(String authHeader) {
        validate(authHeader, props.webhookSecret(), "user_api");
    }

//    public void validateBankHubWebhook(String authHeader) {
//        validate(authHeader, props.bankHub().webhookSecret(), "bank_hub");
//    }

    private void validate(String authHeader, String expectedSecret, String source) {
        if (authHeader == null || authHeader.isBlank()) {
            log.warn("webhook_auth_missing source={}", source);
            throw new WebhookAuthException("Missing Authorization header");
        }

        if (!authHeader.startsWith(APIKEY_PREFIX)) {
            log.warn("webhook_auth_invalid_format source={}", source);
            throw new WebhookAuthException(
                    "Invalid format, expected: Apikey {key}");
        }

        String providedKey = authHeader.substring(APIKEY_PREFIX.length()).trim();

        if (!MessageDigest.isEqual(
                providedKey.getBytes(StandardCharsets.UTF_8),
                expectedSecret.getBytes(StandardCharsets.UTF_8))) {
            log.warn("webhook_auth_secret_mismatch source={}", source);
            throw new WebhookAuthException("Invalid API key");
        }
    }
}
