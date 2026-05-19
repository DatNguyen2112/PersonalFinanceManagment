package BankingSystem.Services;

import BankingSystem.Config.JacksonConfig;
import BankingSystem.Config.Websocket.WsMessage;
import BankingSystem.Entity.Notification;
import BankingSystem.Repositories.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repo;
    private final JacksonConfig config;

    // ── Save (called from KafkaWebSocketBridge) ────────────────────────────

    @Transactional
    public Notification save(Long userId, WsMessage msg, Map<String, Object> rawPayload) {
        try {
            var entity = Notification.builder()
                    .userId(userId)
                    .type(msg.type())
                    .title(msg.title())
                    .message(msg.message())
                    .payload(config.objectMapper().writeValueAsString(rawPayload))
                    .build();

            var saved = repo.save(entity);
            log.info("notification_saved id={} userId={} type={}", saved.getId(), userId, msg.type());
            return saved;

        } catch (Exception ex) {
            log.error("notification_save_failed userId={} error={}", userId, ex.getMessage());
            throw new RuntimeException("Cannot save notification", ex);
        }
    }

    // ── Query ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<NotificationDTO> getNotifications(Long userId, int page, int size) {
        return repo.findByUserIdOrderByCreatedAtDesc(
                        userId, PageRequest.of(page - 1, size))
                .map(NotificationDTO::from);
    }

    @Transactional(readOnly = true)
    public long countUnread(Long userId) {
        return repo.countByUserIdAndReadFalse(userId);
    }

    // ── Mark read ──────────────────────────────────────────────────────────

    @Transactional
    public void markRead(Long id, Long userId) {
        int updated = repo.markRead(id, userId);
        if (updated == 0) log.warn("mark_read_not_found id={} userId={}", id, userId);
    }

    @Transactional
    public void markAllRead(Long userId) {
        int updated = repo.markAllRead(userId);
        log.info("mark_all_read userId={} count={}", userId, updated);
    }

    // ── DTO ────────────────────────────────────────────────────────────────

    public record NotificationDTO(
            Long    id,
            String  type,
            String  title,
            String  message,
            boolean read,
            Instant createdAt
    ) {
        static NotificationDTO from(Notification n) {
            return new NotificationDTO(
                    n.getId(), n.getType(), n.getTitle(),
                    n.getMessage(), n.isRead(), n.getCreatedAt());
        }
    }
}