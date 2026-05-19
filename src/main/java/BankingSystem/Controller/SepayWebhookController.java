package BankingSystem.Controller;

import BankingSystem.Config.AuthWebhook.SepayWebhookAuthValidator;
import BankingSystem.Config.Sepay.SepayProperties;
import BankingSystem.DTO.BankingDTO;
import BankingSystem.JWTConfig.UserDetailsImpl;
import BankingSystem.Services.SepayWebhookService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/sepay")
@Slf4j
@RequiredArgsConstructor
public class SepayWebhookController {

    private final SepayWebhookService webhookService;
    private final SepayWebhookAuthValidator authValidator;

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> receiveWebhook(
            @RequestBody BankingDTO.SepayWebhookPayload payload,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        authValidator.validateUserApiWebhook(authHeader);

        log.info("sepay_webhook_received account={} transferAmount={} transferType={}",
                payload.accountNumber(), payload.transferAmount(), payload.transferType());

        webhookService.process(payload);

        return ResponseEntity.ok(Map.of("success", true));
    }
}
