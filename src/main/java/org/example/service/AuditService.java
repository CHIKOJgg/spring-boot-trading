package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.domain.model.entity.AuditLogEntity;
import org.example.domain.model.entity.UserEntity;
import org.example.dto.response.ApiResponse.AuditLogResponse;
import org.example.repository.AuditLogRepository;
import org.example.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Async
    public void log(String username, String action, String entityType,
                    String entityId, String details, String result) {
        UserEntity user = username != null
                ? userRepository.findByUsername(username).orElse(null)
                : null;

        AuditLogEntity log = AuditLogEntity.builder()
                .user(user)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .details(details)
                .result(result)
                .createdAt(LocalDateTime.now())
                .build();

        auditLogRepository.save(log);
    }

    @Async
    public void logSuccess(String username, String action, String entityType, String entityId) {
        log(username, action, entityType, entityId, null, "SUCCESS");
    }

    @Async
    public void logFailure(String username, String action, String details) {
        log(username, action, null, null, details, "FAILURE");
    }

    public Page<AuditLogResponse> getAll(Pageable pageable) {
        return auditLogRepository.findAll(pageable).map(this::toResponse);
    }

    public Page<AuditLogResponse> getByUser(Long userId, Pageable pageable) {
        return auditLogRepository.findByUserId(userId, pageable).map(this::toResponse);
    }

    private AuditLogResponse toResponse(AuditLogEntity e) {
        return new AuditLogResponse(
                e.getId(),
                e.getUser() != null ? e.getUser().getUsername() : "SYSTEM",
                e.getAction(), e.getEntityType(), e.getEntityId(),
                e.getDetails(), e.getResult(), e.getCreatedAt()
        );
    }
}
