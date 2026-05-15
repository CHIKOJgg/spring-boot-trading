package org.example.repository;

import org.example.domain.model.entity.TradingPositionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TradingPositionRepository extends JpaRepository<TradingPositionEntity, Long> {

    // BUG FIX: toPositionResponse() accesses p.getInstrument().getTicker/getName — LAZY.
    @Query("SELECT p FROM TradingPositionEntity p JOIN FETCH p.instrument " +
           "WHERE p.tradingAccount.id = :tradingAccountId AND p.quantity > 0")
    List<TradingPositionEntity> findByTradingAccountId(Long tradingAccountId);

    Optional<TradingPositionEntity> findByTradingAccountIdAndInstrumentId(
            Long tradingAccountId, Long instrumentId);
}
