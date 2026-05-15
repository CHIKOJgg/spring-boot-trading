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

    // BUG FIX: toTradeResponse() accesses t.getInstrument(), t.getBuyer().getUsername(),
    // t.getSeller().getUsername() — all LAZY. JOIN FETCH all of them in one query.

    @Query("SELECT t FROM TradeEntity t " +
           "JOIN FETCH t.instrument JOIN FETCH t.buyer JOIN FETCH t.seller " +
           "JOIN FETCH t.buyOrder JOIN FETCH t.sellOrder " +
           "WHERE t.buyer.id = :userId OR t.seller.id = :userId " +
           "ORDER BY t.tradedAt DESC")
    List<TradeEntity> findByUserId(Long userId);

    @Query(value = "SELECT t FROM TradeEntity t " +
                   "JOIN FETCH t.instrument JOIN FETCH t.buyer JOIN FETCH t.seller " +
                   "JOIN FETCH t.buyOrder JOIN FETCH t.sellOrder " +
                   "WHERE t.buyer.id = :userId OR t.seller.id = :userId " +
                   "ORDER BY t.tradedAt DESC",
           countQuery = "SELECT COUNT(t) FROM TradeEntity t " +
                        "WHERE t.buyer.id = :userId OR t.seller.id = :userId")
    Page<TradeEntity> findByUserIdPaged(Long userId, Pageable pageable);

    @Query("SELECT t FROM TradeEntity t " +
           "JOIN FETCH t.instrument JOIN FETCH t.buyer JOIN FETCH t.seller " +
           "JOIN FETCH t.buyOrder JOIN FETCH t.sellOrder " +
           "WHERE t.instrument.ticker = :ticker ORDER BY t.tradedAt DESC")
    List<TradeEntity> findByInstrumentTickerOrderByTradedAtDesc(String ticker);

    @Query(value = "SELECT t FROM TradeEntity t " +
                   "JOIN FETCH t.instrument JOIN FETCH t.buyer JOIN FETCH t.seller " +
                   "JOIN FETCH t.buyOrder JOIN FETCH t.sellOrder " +
                   "WHERE t.instrument.ticker = :ticker ORDER BY t.tradedAt DESC",
           countQuery = "SELECT COUNT(t) FROM TradeEntity t WHERE t.instrument.ticker = :ticker")
    Page<TradeEntity> findByInstrumentTickerOrderByTradedAtDesc(String ticker, Pageable pageable);

    @Query("SELECT t FROM TradeEntity t " +
           "JOIN FETCH t.instrument JOIN FETCH t.buyer JOIN FETCH t.seller " +
           "JOIN FETCH t.buyOrder JOIN FETCH t.sellOrder " +
           "WHERE t.instrument.ticker = :ticker ORDER BY t.tradedAt DESC")
    List<TradeEntity> findTop50ByInstrumentTickerOrderByTradedAtDesc(String ticker);

    Page<TradeEntity> findByTradedAtBetween(LocalDateTime from, LocalDateTime to, Pageable pageable);

    @Query("SELECT SUM(t.price * t.quantity) FROM TradeEntity t " +
           "WHERE t.instrument.ticker = :ticker AND t.tradedAt >= :from AND t.tradedAt <= :to")
    java.math.BigDecimal sumVolumeByTickerAndPeriod(String ticker, LocalDateTime from, LocalDateTime to);
}
