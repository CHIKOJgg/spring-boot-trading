package org.example.repository;

import org.example.domain.model.entity.CashOperationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CashOperationRepository extends JpaRepository<CashOperationEntity, Long> {
    List<CashOperationEntity> findByBankAccountIdOrderByCreatedAtDesc(Long bankAccountId);
    Page<CashOperationEntity> findByUserId(Long userId, Pageable pageable);
    Page<CashOperationEntity> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to, Pageable pageable);
}
