package BankingSystem.Config.Kafka;

import BankingSystem.Config.Websocket.WsMessage;
import BankingSystem.Entity.Budget;
import BankingSystem.Entity.SepayTransaction.SepayTransaction;
import BankingSystem.Repositories.BudgetRepository;
import BankingSystem.Repositories.SepayTransactionRepository;
import BankingSystem.Services.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class KafkaWebsocketBridge {

    private final SimpMessagingTemplate      ws;
    private final NotificationService        notificationService;
    private final SepayTransactionRepository transactionRepository;
    private final BudgetRepository           budgetRepository;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    // ── 1. Transaction ──────────────────────────────────────────────────────
    @KafkaListener(topics = "banking.sepay.transaction", groupId = "ws-bridge-group")
    public void onTransaction(ConsumerRecord<String, Map<String, Object>> record,
                              Acknowledgment ack) {
        try {
            Long userId = toLong(record.key());
            Long txId   = toLong(record.value().get("transactionId"));

            // Load full entity from DB
            SepayTransaction tx = txId != null
                    ? transactionRepository.findByIdWithCategory(txId).orElse(null)
                    : null;

            String text          = tx != null ? formatTransaction(tx) : "Giao dịch mới";
            Map<String, Object> data = tx != null ? toMap(tx) : record.value();

            var msg = WsMessage.transaction(text, data);

            if (userId != null) notificationService.save(userId, msg, data);
            ws.convertAndSend("/topic/transactions", msg);
            if (userId != null)
                ws.convertAndSendToUser(userId.toString(), "/topic/transactions", msg);

            log.info("ws_push TRANSACTION userId={} txId={}", userId, txId);
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("ws_push_failed TRANSACTION error={}", ex.getMessage(), ex);
        }
    }

    // ── 2. Budget alert ─────────────────────────────────────────────────────
    @KafkaListener(topics = "banking.sepay.budget-alert", groupId = "ws-bridge-group")
    public void onBudgetAlert(ConsumerRecord<String, Map<String, Object>> record,
                              Acknowledgment ack) {
        try {
            Long userId   = toLong(record.key());
            Long budgetId = toLong(record.value().get("budgetId"));
            int  usage    = toInt(record.value().get("usagePercent"));

            // Load full budget entity from DB
            Budget budget = budgetId != null
                    ? budgetRepository.findByIdWithCategory(budgetId).orElse(null)
                    : null;

            String text          = budget != null ? formatBudgetAlert(budget, usage) : "Cảnh báo ngân sách %d%%".formatted(usage);
            Map<String, Object> data = budget != null ? toMap(budget, usage) : record.value();

            var msg = WsMessage.budgetAlert(text, data);

            if (userId != null) notificationService.save(userId, msg, data);
            ws.convertAndSend("/topic/budget-alerts", msg);
            if (userId != null)
                ws.convertAndSendToUser(userId.toString(), "/topic/budget-alerts", msg);

            log.info("ws_push BUDGET_ALERT userId={} budgetId={} usage={}%", userId, budgetId, usage);
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("ws_push_failed BUDGET_ALERT error={}", ex.getMessage(), ex);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Formatters
    // ═══════════════════════════════════════════════════════════════════════

    private String formatTransaction(SepayTransaction tx) {
        boolean isIn      = tx.getAmountIn() != null && tx.getAmountIn().signum() > 0;
        BigDecimal amount = isIn ? tx.getAmountIn() : tx.getAmountOut();
        String sign       = isIn ? "+" : "-";
        String account    = maskAccount(tx.getAccountNumber());
        String bank       = tx.getBankBrandName() != null ? tx.getBankBrandName() : "";
        String content    = tx.getTransactionContent() != null ? tx.getTransactionContent() : "Không có nội dung";
        String category   = tx.getCategory() != null ? tx.getCategory().getName() : "Chưa phân loại";
        String time       = tx.getTransactionDate() != null ? tx.getTransactionDate().format(DATE_FMT) : "";

        // e.g. "+500,000đ  •  TK 9704...1234 (Techcombank)  •  Chuyển tiền  •  Ăn uống  •  14:30 19/05/2026"
        return "%s%sđ  •  TK %s (%s)  •  %s  •  %s  •  %s"
                .formatted(sign, fmt(amount), account, bank, content, category, time);
    }

    private String formatBudgetAlert(Budget budget, int usagePercent) {
        String category  = budget.getCategory() != null ? budget.getCategory().getName() : "Tổng chi tiêu";
        BigDecimal limit = budget.getLimitAmount();
        int month        = budget.getMonth();
        int year         = budget.getYear();

        // e.g. "Danh mục Nhà ở đã dùng 91% ngân sách tháng 5/2026 (hạn mức: 5,500,000đ)"
        return "Danh mục %s đã dùng %d%% ngân sách tháng %d/%d (hạn mức: %sđ)"
                .formatted(category, usagePercent, month, year, fmt(limit));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Entity → Map (for WsMessage.data sent to client)
    // ═══════════════════════════════════════════════════════════════════════

    private Map<String, Object> toMap(SepayTransaction tx) {
        return Map.of(
                "transactionId",      tx.getId(),
                "accountNumber",      tx.getAccountNumber(),
                "bankBrandName",      tx.getBankBrandName() != null ? tx.getBankBrandName() : "",
                "amountIn",           tx.getAmountIn()  != null ? tx.getAmountIn()  : BigDecimal.ZERO,
                "amountOut",          tx.getAmountOut() != null ? tx.getAmountOut() : BigDecimal.ZERO,
                "transactionContent", tx.getTransactionContent() != null ? tx.getTransactionContent() : "",
                "transactionDate",    tx.getTransactionDate() != null ? tx.getTransactionDate().format(DATE_FMT) : "",
                "category",           tx.getCategory() != null ? tx.getCategory().getName() : "Chưa phân loại",
                "direction",          tx.getDirection().name(),
                "accumulated",        tx.getAccumulated() != null ? tx.getAccumulated() : BigDecimal.ZERO
        );
    }

    private Map<String, Object> toMap(Budget budget, int usagePercent) {
        return Map.of(
                "budgetId",       budget.getId(),
                "categoryName",   budget.getCategory() != null ? budget.getCategory().getName() : "Tổng",
                "month",          budget.getMonth(),
                "year",           budget.getYear(),
                "limitAmount",    budget.getLimitAmount(),
                "usagePercent",   usagePercent,
                "alertThreshold", budget.getAlertThreshold()
        );
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    private String maskAccount(String a) {
        if (a == null || a.length() < 6) return a;
        return a.substring(0, 4) + "..." + a.substring(a.length() - 4);
    }

    private String fmt(BigDecimal v) {
        return v == null ? "0" : String.format("%,.0f", v);
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        try { return Long.parseLong(val.toString()); }
        catch (Exception e) { return null; }
    }

    private int toInt(Object val) {
        if (val == null) return 0;
        try { return Integer.parseInt(val.toString()); }
        catch (Exception e) { return 0; }
    }
}