package BankingSystem.Controller;

import BankingSystem.Config.Sepay.SepayBankHubProperties;
import BankingSystem.DTO.SepayBankHub;
import BankingSystem.JWTConfig.UserDetailsImpl;
import BankingSystem.Services.SepayBankHubService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/bank-hub")
@RequiredArgsConstructor
@Slf4j
public class SepayBankHubController {

    private final SepayBankHubService bankHubService;
    private final SepayBankHubProperties props;

    // ── Khởi tạo liên kết — frontend gọi để lấy hostedLinkUrl ────────────

    @PostMapping("/init-link")
    public ResponseEntity<SepayBankHub.BankHubInitResponse> initLink(
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails u) {
        return ResponseEntity.ok(bankHubService.initLinkAccount(u.getUserId()));
    }

    // ── Khởi tạo hủy liên kết ─────────────────────────────────────────────

    @PostMapping("/init-unlink")
    public ResponseEntity<SepayBankHub.BankHubInitResponse> initUnlink(
            @Valid @RequestBody SepayBankHub.BankHubUnlinkRequest req,
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails u) {
        return ResponseEntity.ok(
                bankHubService.initUnlinkAccount(u.getUserId(), req.bankAccountXid()));
    }

    // ── Callback — SePay redirect về sau khi user hoàn thành ──────────────

    @GetMapping("/callback")
    public ResponseEntity<Map<String, String>> callback(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String link_token_xid) {
        log.info("bank_hub_callback status={} linkTokenXid={}", status, link_token_xid);
        // Redirect về frontend hoặc trả về thông báo
        return ResponseEntity.ok(Map.of(
                "status", status != null ? status : "completed",
                "message", "Liên kết ngân hàng hoàn tất. Bạn có thể đóng cửa sổ này."));
    }

    // ── Webhook — SePay Bank Hub gọi vào khi có sự kiện liên kết ─────────

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> receiveWebhook(
            @RequestBody SepayBankHub.BankHubWebhookPayload payload,
            @RequestHeader(value = "Authorization", required = false) String auth) {

        if (!isValidAuth(auth)) {
            log.warn("bank_hub_webhook_unauthorized");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false));
        }

        bankHubService.processWebhookEvent(payload);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ── Sync thủ công từ Bank Hub về DB ───────────────────────────────────

    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> sync(
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails u) {
        int synced = bankHubService.syncLinkedAccountsFromBankHub(u.getUserId());
        return ResponseEntity.ok(Map.of("synced", synced));
    }

    private boolean isValidAuth(String auth) {
        return ("Apikey " + props.webhookSecret()).equals(auth);
    }
}
