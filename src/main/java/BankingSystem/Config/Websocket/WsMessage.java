package BankingSystem.Config.Websocket;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WsMessage(
        String  type,       // TRANSACTION | BUDGET_ALERT | SYNC
        String  title,      // short heading
        String  message,    // human-readable Vietnamese sentence
        Object  data,       // raw structured payload
        Instant timestamp
) {
    public static WsMessage transaction(String msg, Object data) {
        return new WsMessage("TRANSACTION", "Giao dịch mới", msg, data, Instant.now());
    }

    public static WsMessage budgetAlert(String msg, Object data) {
        return new WsMessage("BUDGET_ALERT", "Cảnh báo ngân sách", msg, data, Instant.now());
    }

    public static WsMessage sync(String msg, Object data) {
        return new WsMessage("SYNC", "Đồng bộ giao dịch", msg, data, Instant.now());
    }
}