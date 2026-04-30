package BankingSystem.Controller;

import BankingSystem.Config.Sepay.SepayProperties;
import BankingSystem.DTO.BankingDTO;
import BankingSystem.Services.SepayWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/sepay")
@Slf4j
@RequiredArgsConstructor
public class SepayWebhookController {

    private final SepayWebhookService webhookService;
    private final SepayProperties props;

    /**
     * SePay gọi vào endpoint này khi có giao dịch mới.
     * Xác thực qua header Authorization: Apikey {WEBHOOK_SECRET}
     */
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> receiveWebhook(
            @RequestBody BankingDTO.SepayWebhookPayload payload,
            @RequestHeader(value = "Authorization", required = false) String auth) {

        if (!isValidWebhookAuth(auth)) {
            log.warn("sepay_webhook_unauthorized");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Unauthorized"));
        }

        log.info("sepay_webhook_received account={} amountIn={} amountOut={}",
                payload.accountNumber(), payload.amountIn(), payload.amountOut());

        webhookService.process(payload);

        return ResponseEntity.ok(Map.of("success", true));
    }

    private boolean isValidWebhookAuth(String auth) {
        return ("Apikey " + props.webhookSecret()).equals(auth);
    }
}
