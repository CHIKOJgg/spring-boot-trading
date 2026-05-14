package org.example.repository;

import org.example.domain.model.entity.OrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, String> {
    List<OrderEntity> findByUserId(Long userId);
    Page<OrderEntity> findByUserId(Long userId, Pageable pageable);
    List<OrderEntity> findByUserIdAndStatus(Long userId, String status);

    @Query("SELECT o FROM OrderEntity o WHERE o.user.id = :userId AND o.status IN ('PENDING','PARTIALLY_FILLED')")
    List<OrderEntity> findActiveByUserId(Long userId);

    @Query("SELECT o FROM OrderEntity o WHERE o.instrument.ticker = :ticker AND o.status IN ('PENDING','PARTIALLY_FILLED')")
    List<OrderEntity> findActiveByTicker(String ticker);

    @Query("SELECT o FROM OrderEntity o WHERE o.status IN ('PENDING','PARTIALLY_FILLED')")
    List<OrderEntity> findAllActive();

    Page<OrderEntity> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to, Pageable pageable);

    @Query("SELECT o FROM OrderEntity o WHERE o.tradingAccount.id = :accountId ORDER BY o.createdAt DESC")
    List<OrderEntity> findByTradingAccountId(Long accountId);
}
