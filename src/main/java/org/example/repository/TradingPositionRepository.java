package org.example.repository;

import org.example.domain.model.entity.TradingPositionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface TradingPositionRepository extends JpaRepository<TradingPositionEntity, Long> {
    List<TradingPositionEntity> findByTradingAccountId(Long tradingAccountId);

    Optional<TradingPositionEntity> findByTradingAccountIdAndInstrumentId(
            Long tradingAccountId, Long instrumentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM TradingPositionEntity p WHERE p.tradingAccount.id = :accountId AND p.instrument.id = :instrumentId")
    Optional<TradingPositionEntity> findForUpdate(Long accountId, Long instrumentId);
}
