package BankingSystem.Config;
import BankingSystem.DTO.BankingDTO;
import BankingSystem.Exception.SepayApiExecption;
import com.google.common.annotations.Beta;
import com.google.common.util.concurrent.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class SepayApiClient {

    private final SepayProperties props;
    private final RestClient restClient;
    private final RateLimiter rateLimiter;  // Guava RateLimiter

    @Beta
    static RateLimiter sepayRateLimiter(SepayProperties props) {
        return RateLimiter.create(props.rateLimitPerSecond());
    }

    public BankingDTO.SepayTransactionListResponse listTransactions(BankingDTO.SepayListRequest req) {
        rateLimiter.acquire();
        log.info("sepay_pull account={} since_id={}", req.accountNumber(), req.sinceId());
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/transactions/list")
                        .queryParamIfPresent("account_number", Optional.ofNullable(req.accountNumber()))
                        .queryParamIfPresent("since_id", Optional.ofNullable(req.sinceId()))
                        .queryParamIfPresent("limit", Optional.of(req.limit()))
                        .queryParamIfPresent("transaction_date_min",
                                Optional.ofNullable(req.dateMin()).map(Object::toString))
                        .queryParamIfPresent("transaction_date_max",
                                Optional.ofNullable(req.dateMax()).map(Object::toString))
                        .build())
                .header("Authorization", "Apikey " + props.token())
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    throw new SepayApiExecption("SePay 4xx: " + response.getStatusCode());
                })
                .body(BankingDTO.SepayTransactionListResponse.class);
    }

    public BankingDTO.SepayTransactionDetailResponse getTransaction(Long sepayId) {
        rateLimiter.acquire();
        return restClient.get()
                .uri("/transactions/details/{id}", sepayId)
                .header("Authorization", "Apikey " + props.token())
                .retrieve()
                .body(BankingDTO.SepayTransactionDetailResponse.class);
    }
}
