package org.example.repository;

import org.example.domain.model.entity.AuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {
    Page<AuditLogEntity> findByUserId(Long userId, Pageable pageable);
    Page<AuditLogEntity> findByAction(String action, Pageable pageable);
    Page<AuditLogEntity> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to, Pageable pageable);
    Page<AuditLogEntity> findAll(Pageable pageable);
}
