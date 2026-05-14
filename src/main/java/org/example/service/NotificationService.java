package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.domain.model.entity.NotificationEntity;
import org.example.domain.model.entity.UserEntity;
import org.example.dto.response.ApiResponse.NotificationResponse;
import org.example.repository.NotificationRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Async
    @Transactional
    public void notify(UserEntity user, String type, String title, String message) {
        NotificationEntity n = NotificationEntity.builder()
                .user(user)
                .type(type)
                .title(title)
                .message(message)
                .createdAt(LocalDateTime.now())
                .build();
        notificationRepository.save(n);

        // push over WebSocket
        try {
            messagingTemplate.convertAndSendToUser(
                    user.getUsername(), "/queue/notifications", toResponse(n));
        } catch (Exception ignored) {}
    }

    public List<NotificationResponse> getForUser(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toResponse).toList();
    }

    public long countUnread(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.findByUserIdAndIsReadFalse(userId)
                .forEach(n -> n.setIsRead(true));
    }

    private NotificationResponse toResponse(NotificationEntity n) {
        return new NotificationResponse(n.getId(), n.getTitle(), n.getMessage(),
                n.getType(), n.getIsRead(), n.getCreatedAt());
    }
}
