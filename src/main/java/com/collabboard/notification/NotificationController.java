package com.collabboard.notification;

import com.collabboard.auth.CurrentUser;
import com.collabboard.notification.NotificationService.NotificationDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;
    private final CurrentUser currentUser;

    public NotificationController(NotificationService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<NotificationDto> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                      @RequestParam(defaultValue = "50") int limit) {
        return service.list(currentUser.get().id(), unreadOnly, limit);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", service.unreadCount(currentUser.get().id()));
    }

    @PostMapping("/{id}/read")
    public NotificationDto markRead(@PathVariable Long id) {
        return service.markRead(currentUser.get().id(), id);
    }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead() {
        return Map.of("updated", service.markAllRead(currentUser.get().id()));
    }
}
