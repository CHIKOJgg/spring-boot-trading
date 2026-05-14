package org.example.repository;

import org.example.domain.model.entity.TradingAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface TradingAccountRepository extends JpaRepository<TradingAccountEntity, Long> {
    List<TradingAccountEntity> findByUserId(Long userId);
    Optional<TradingAccountEntity> findByAccountNumber(String accountNumber);
    boolean existsByAccountNumber(String accountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TradingAccountEntity t WHERE t.id = :id")
    Optional<TradingAccountEntity> findByIdForUpdate(Long id);
}
