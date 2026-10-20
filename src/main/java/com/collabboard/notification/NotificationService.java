package com.collabboard.notification;

import com.collabboard.common.ApiException;
import com.collabboard.user.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Service
public class NotificationService {

    public record NotificationDto(Long id, NotificationType type, String title, String message, String link,
                                  boolean read, Instant createdAt) {
        static NotificationDto of(Notification n) {
            return new NotificationDto(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.getLink(),
                    n.getReadAt() != null, n.getCreatedAt());
        }
    }

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void notify(User user, NotificationType type, String title, String message, String link) {
        repository.save(new Notification(user, type, title, message, link, null));
    }

    @Transactional
    public void notifyAll(Collection<User> users, NotificationType type, String title, String message, String link) {
        for (User u : users) {
            notify(u, type, title, message, link);
        }
    }

    /** Creates the notification only once for the given key (used by scheduled reminders). */
    @Transactional
    public boolean notifyOnce(User user, String dedupeKey, NotificationType type, String title, String message, String link) {
        if (repository.existsByDedupeKey(dedupeKey)) {
            return false;
        }
        repository.save(new Notification(user, type, title, message, link, dedupeKey));
        return true;
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> list(Long userId, boolean unreadOnly, int limit) {
        PageRequest page = PageRequest.of(0, Math.max(1, Math.min(limit, 200)));
        List<Notification> list = unreadOnly
                ? repository.findByUserIdAndReadAtIsNullOrderByCreatedAtDescIdDesc(userId, page)
                : repository.findByUserIdOrderByCreatedAtDescIdDesc(userId, page);
        return list.stream().map(NotificationDto::of).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return repository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public NotificationDto markRead(Long userId, Long notificationId) {
        Notification n = repository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> ApiException.notFound("Notification"));
        n.markRead(Instant.now());
        return NotificationDto.of(n);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return repository.markAllRead(userId, Instant.now());
    }
}
