package BankingSystem.Config.Sepay;
import BankingSystem.DTO.BankingDTO;
import BankingSystem.Exception.SepayApiException;
import BankingSystem.Exception.SepayRateLimitException;
import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.Optional;

@Component
@Slf4j
public class SepayApiClient {

    private final SepayProperties props;
    private final RestClient restClient;
    private final RateLimiter rateLimiter;

    public SepayApiClient(
            SepayProperties props,
            @Qualifier("sepayRestClient") RestClient restClient,  // ← chỉ định đúng bean
            RateLimiter rateLimiter) {
        this.props = props;
        this.restClient = restClient;
        this.rateLimiter = rateLimiter;
    }

    public BankingDTO.SepayTransactionListResponse listTransactions(BankingDTO.SepayListRequest req) {
        try {
            rateLimiter.acquire();
            log.info("sepay_pull account={} since_id={}",
                    req.accountNumber(), req.sinceId());

            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/transactions/list")
                            .queryParamIfPresent("account_number",
                                    Optional.ofNullable(req.accountNumber()))
                            .queryParamIfPresent("since_id",
                                    Optional.ofNullable(req.sinceId()))
                            .queryParamIfPresent("limit", Optional.of(req.limit()))
                            .queryParamIfPresent("transaction_date_min",
                                    Optional.ofNullable(req.dateMin()).map(LocalDate::toString))
                            .queryParamIfPresent("transaction_date_max",
                                    Optional.ofNullable(req.dateMax()).map(LocalDate::toString))
                            .build())
                    .header("Authorization", "Apikey " + props.token())
                    .retrieve()
                    .onStatus(status -> status.value() == 429, (request, response) -> {
                        throw new SepayRateLimitException();
                    })
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        throw new SepayApiException(
                                "SePay 4xx: " + response.getStatusCode());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                        throw new SepayApiException(
                                "SePay 5xx: " + response.getStatusCode());
                    })
                    .body(BankingDTO.SepayTransactionListResponse.class);

        } catch (SepayRateLimitException | SepayApiException ex) {
            throw ex;   // re-throw domain exceptions
        } catch (Exception ex) {
            throw new SepayApiException("Unexpected error calling SePay API", ex);
        }
    }
}
