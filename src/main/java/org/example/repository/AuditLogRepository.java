package org.example.repository;

import org.example.domain.model.entity.AuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {

    // BUG FIX: toResponse() accesses e.getUser().getUsername() — LAZY.
    // LEFT JOIN FETCH because user can be null (SYSTEM actions).

    @Query(value = "SELECT a FROM AuditLogEntity a LEFT JOIN FETCH a.user ORDER BY a.createdAt DESC",
           countQuery = "SELECT COUNT(a) FROM AuditLogEntity a")
    Page<AuditLogEntity> findAll(Pageable pageable);

    @Query(value = "SELECT a FROM AuditLogEntity a LEFT JOIN FETCH a.user " +
                   "WHERE a.user.id = :userId ORDER BY a.createdAt DESC",
           countQuery = "SELECT COUNT(a) FROM AuditLogEntity a WHERE a.user.id = :userId")
    Page<AuditLogEntity> findByUserId(Long userId, Pageable pageable);

    @Query(value = "SELECT a FROM AuditLogEntity a LEFT JOIN FETCH a.user " +
                   "WHERE a.action = :action ORDER BY a.createdAt DESC",
           countQuery = "SELECT COUNT(a) FROM AuditLogEntity a WHERE a.action = :action")
    Page<AuditLogEntity> findByAction(String action, Pageable pageable);

    @Query(value = "SELECT a FROM AuditLogEntity a LEFT JOIN FETCH a.user " +
                   "WHERE a.createdAt BETWEEN :from AND :to ORDER BY a.createdAt DESC",
           countQuery = "SELECT COUNT(a) FROM AuditLogEntity a WHERE a.createdAt BETWEEN :from AND :to")
    Page<AuditLogEntity> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to, Pageable pageable);
}
