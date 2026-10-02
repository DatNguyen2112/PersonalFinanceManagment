package BankingSystem.Services.ImplService;

import BankingSystem.Config.Websocket.WsMessage;
import BankingSystem.Entity.Notification;
import BankingSystem.Services.NotificationService;
import org.springframework.data.domain.Page;

import java.util.Map;

public interface ImplNotificationService {
    Notification save(Long userId, WsMessage msg, Map<String, Object> rawPayload);
    Page<NotificationService.NotificationDTO> getNotifications(Long userId, int page, int size);
    long countUnread(Long userId);
    void markRead(Long id, Long userId);
    void markAllRead(Long userId);

}
