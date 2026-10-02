package BankingSystem.Controller;

import BankingSystem.JWTConfig.UserDetailsImpl;
import BankingSystem.Services.NotificationService;
import BankingSystem.Services.NotificationService.NotificationDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    // GET /api/v1/notifications?page=1&size=20
    @GetMapping
    public ResponseEntity<Page<NotificationDTO>> list(
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails u,
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(service.getNotifications(u.getUserId(), page, size));
    }

    // GET /api/v1/notifications/unread-count  → { "count": 5 }
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails u) {

        return ResponseEntity.ok(Map.of("count", service.countUnread(u.getUserId())));
    }

    // PATCH /api/v1/notifications/{id}/read
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails u,
            @PathVariable Long id) {

        service.markRead(id, u.getUserId());
        return ResponseEntity.noContent().build();
    }

    // PATCH /api/v1/notifications/read-all
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllRead(
            @AuthenticationPrincipal UserDetailsImpl.BankingUserDetails u) {

        service.markAllRead(u.getUserId());
        return ResponseEntity.noContent().build();
    }
}