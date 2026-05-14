package org.example.repository;

import org.example.domain.model.entity.TradeEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TradeRepository extends JpaRepository<TradeEntity, String> {
    @Query("SELECT t FROM TradeEntity t WHERE t.buyer.id = :userId OR t.seller.id = :userId ORDER BY t.tradedAt DESC")
    List<TradeEntity> findByUserId(Long userId);

    @Query("SELECT t FROM TradeEntity t WHERE t.buyer.id = :userId OR t.seller.id = :userId ORDER BY t.tradedAt DESC")
    Page<TradeEntity> findByUserIdPaged(Long userId, Pageable pageable);

    List<TradeEntity> findByInstrumentTickerOrderByTradedAtDesc(String ticker);
    Page<TradeEntity> findByInstrumentTickerOrderByTradedAtDesc(String ticker, Pageable pageable);

    List<TradeEntity> findTop50ByInstrumentTickerOrderByTradedAtDesc(String ticker);

    Page<TradeEntity> findByTradedAtBetween(LocalDateTime from, LocalDateTime to, Pageable pageable);

    @Query("SELECT SUM(t.price * t.quantity) FROM TradeEntity t WHERE t.instrument.ticker = :ticker AND t.tradedAt >= :from AND t.tradedAt <= :to")
    java.math.BigDecimal sumVolumeByTickerAndPeriod(String ticker, LocalDateTime from, LocalDateTime to);
}
