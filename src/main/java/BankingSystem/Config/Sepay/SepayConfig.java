package BankingSystem.Config.Sepay;

import com.google.common.util.concurrent.RateLimiter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(SepayProperties.class)
public class SepayConfig {

    @Bean
    public RestClient sepayRestClient(SepayProperties props) {
        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @Bean
    public RateLimiter sepayRateLimiter(SepayProperties props) {
        return RateLimiter.create(props.rateLimitPerSecond());
    }
}
