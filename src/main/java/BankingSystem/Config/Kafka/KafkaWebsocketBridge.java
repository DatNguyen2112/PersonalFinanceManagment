package BankingSystem.Config.Kafka;

import BankingSystem.Config.Websocket.WsMessage;
import BankingSystem.Services.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class KafkaWebsocketBridge {

    private final SimpMessagingTemplate ws;
    private final NotificationService   notificationService;

    // ── 1. Transaction ──────────────────────────────────────────────────────
    @KafkaListener(topics = "banking.sepay.transaction", groupId = "ws-bridge-group")
    public void onTransaction(Map<String, Object> payload, Acknowledgment ack) {
        try {
            Long userId = toLong(payload.get("userId"));
            var  msg    = WsMessage.transaction(formatTransaction(payload), payload);

            // 1. Save to DB
            if (userId != null) notificationService.save(userId, msg, payload);

            // 2. Push to WebSocket
            ws.convertAndSend("/topic/transactions", msg);
            if (userId != null)
                ws.convertAndSendToUser(userId.toString(), "/topic/transactions", msg);

            log.info("ws_push TRANSACTION userId={}", userId);
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("ws_push_failed TRANSACTION error={}", ex.getMessage());
        }
    }

    // ── 2. Budget alert ─────────────────────────────────────────────────────
    @KafkaListener(topics = "banking.sepay.budget-alert", groupId = "ws-bridge-group")
    public void onBudgetAlert(Map<String, Object> payload, Acknowledgment ack) {
        try {
            Long userId = toLong(payload.get("userId"));
            var  msg    = WsMessage.budgetAlert(formatBudgetAlert(payload), payload);

            // 1. Save to DB
            if (userId != null) notificationService.save(userId, msg, payload);

            // 2. Push to WebSocket (broadcast + per-user)
            ws.convertAndSend("/topic/budget-alerts", msg);
            if (userId != null)
                ws.convertAndSendToUser(userId.toString(), "/topic/budget-alerts", msg);

            log.info("ws_push BUDGET_ALERT userId={}", userId);
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("ws_push_failed BUDGET_ALERT error={}", ex.getMessage());
        }
    }

    // ── Formatters ──────────────────────────────────────────────────────────

    private String formatTransaction(Map<String, Object> p) {
        var amountIn  = toBigDecimal(p.get("amountIn"));
        var amountOut = toBigDecimal(p.get("amountOut"));
        var content   = str(p.get("transactionContent"), "Không có nội dung");
        var account   = str(p.get("accountNumber"), "");

        return amountIn.signum() > 0
                ? "+%sđ  •  TK %s  •  %s".formatted(fmt(amountIn),  maskAccount(account), content)
                : "-%sđ  •  TK %s  •  %s".formatted(fmt(amountOut), maskAccount(account), content);
    }

    private String formatBudgetAlert(Map<String, Object> p) {
        var category = str(p.get("categoryName"), "Chi tiêu");
        var usage    = str(p.get("usagePercent"),  "?");
        var spent    = toBigDecimal(p.get("spent"));
        var limit    = toBigDecimal(p.get("limitAmount"));
        return "Danh mục %s đã dùng %s%% ngân sách (%sđ / %sđ)"
                .formatted(category, usage, fmt(spent), fmt(limit));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private String maskAccount(String a) {
        if (a == null || a.length() < 6) return a;
        return a.substring(0, 4) + "..." + a.substring(a.length() - 4);
    }

    private String fmt(BigDecimal v) {
        return v == null ? "0" : String.format("%,.0f", v);
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal bd) return bd;
        try { return new BigDecimal(val.toString()); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        try { return Long.parseLong(val.toString()); }
        catch (Exception e) { return null; }
    }

    private String str(Object val, String fallback) {
        return val != null ? val.toString() : fallback;
    }
}