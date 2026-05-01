package BankingSystem.Config.Sepay;

import BankingSystem.DTO.BankingDTO;
import BankingSystem.DTO.SepayBankHub;
import BankingSystem.Exception.SepayApiExecption;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class SepayBankHubClient {

    private final SepayBankHubProperties props;
    private final RestClient bankHubRestClient;

    // Cache access token trong memory (tránh gọi /v1/token liên tục)
    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    // ── Access Token ───────────────────────────────────────────────────────

    public String getAccessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(30))) {
            return cachedToken;
        }
        return refreshAccessToken();
    }

    private synchronized String refreshAccessToken() {
        // Double-check sau khi vào synchronized
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(30))) {
            return cachedToken;
        }

        String credentials = props.clientId() + ":" + props.clientSecret();
        String basicAuth = "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        log.info("bank_hub_token_refresh");

        var response = bankHubRestClient.post()
                .uri("/token")
                .header("Authorization", basicAuth)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    throw new SepayApiExecption("BankHub auth failed: " + res.getStatusCode());
                })
                .body(SepayBankHub.BankHubTokenResponse.class);

        if (response == null) {
            throw new SepayApiExecption("BankHub token response is null");
        }

        cachedToken = response.accessToken();
        tokenExpiresAt = Instant.now().plusSeconds(response.expiresIn());
        log.info("bank_hub_token_refreshed expires_in={}s", response.expiresIn());

        return cachedToken;
    }

    // ── Link Token ─────────────────────────────────────────────────────────

    public SepayBankHub.BankHubLinkTokenResponse createLinkToken(String purpose,
                                                               String bankAccountXid,
                                                               String redirectUri) {
        var body = new HashMap<String, Object>();
        body.put("company_xid", props.companyXid());
        body.put("purpose", purpose);
        body.put("language", "vi");
        body.put("is_mobile_app", 0);
        body.put("completion_redirect_uri",
                redirectUri != null ? redirectUri : props.completionRedirectUri());

        if (bankAccountXid != null) {
            body.put("bank_account_xid", bankAccountXid);
        }

        log.info("bank_hub_create_link_token purpose={}", purpose);

        return bankHubRestClient.post()
                .uri("/link-token/create")
                .header("Authorization", "Bearer " + getAccessToken())
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    throw new SepayApiExecption("BankHub create link token failed: "
                            + res.getStatusCode());
                })
                .body(SepayBankHub.BankHubLinkTokenResponse.class);
    }

    // ── Bank Accounts ──────────────────────────────────────────────────────

    public List<SepayBankHub.BankHubBankAccountItem> listLinkedAccounts() {
        log.info("bank_hub_list_accounts");

        var response = bankHubRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/bank-account")
                        .queryParam("company_xid", props.companyXid())
                        .queryParam("per_page", 100)
                        .build())
                .header("Authorization", "Bearer " + getAccessToken())
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        if (response == null || !response.containsKey("data")) return List.of();

        // parse data array
        var objectMapper = new ObjectMapper();
        var dataList = (List<?>) response.get("data");
        return dataList.stream()
                .map(item -> objectMapper.convertValue(item,
                        SepayBankHub.BankHubBankAccountItem.class))
                .toList();
    }

    public SepayBankHub.BankHubBankAccountItem getBankAccount(String bankAccountXid) {
        log.info("bank_hub_get_account xid={}", bankAccountXid);

        var response = bankHubRestClient.get()
                .uri("/bank-account/{xid}", bankAccountXid)
                .header("Authorization", "Bearer " + getAccessToken())
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    throw new EntityNotFoundException(
                            "BankHub account not found xid=" + bankAccountXid);
                })
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        var objectMapper = new ObjectMapper();
        return objectMapper.convertValue(
                response != null ? response.get("data") : null,
                SepayBankHub.BankHubBankAccountItem.class);
    }
}
