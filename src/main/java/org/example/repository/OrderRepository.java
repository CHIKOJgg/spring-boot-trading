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

    // BUG FIX: All query methods that are called outside a transaction
    // (service methods without @Transactional) triggered LazyInitializationException
    // when toOrderResponse() accessed o.getInstrument().getTicker().
    // FIX: JOIN FETCH eagerly loads instrument and user within the same SQL query,
    // so the Hibernate session is not needed after the query returns.

    @Query("SELECT o FROM OrderEntity o JOIN FETCH o.instrument JOIN FETCH o.user " +
           "WHERE o.user.id = :userId AND o.status IN ('PENDING','PARTIALLY_FILLED') " +
           "ORDER BY o.createdAt DESC")
    List<OrderEntity> findActiveByUserId(Long userId);

    @Query("SELECT o FROM OrderEntity o JOIN FETCH o.instrument JOIN FETCH o.user " +
           "WHERE o.user.id = :userId ORDER BY o.createdAt DESC")
    List<OrderEntity> findByUserIdFetched(Long userId);

    // For paged queries, JOIN FETCH requires a count query without the fetch
    @Query(value = "SELECT o FROM OrderEntity o JOIN FETCH o.instrument JOIN FETCH o.user " +
                   "WHERE o.user.id = :userId ORDER BY o.createdAt DESC",
           countQuery = "SELECT COUNT(o) FROM OrderEntity o WHERE o.user.id = :userId")
    Page<OrderEntity> findByUserId(Long userId, Pageable pageable);

    @Query("SELECT o FROM OrderEntity o JOIN FETCH o.instrument JOIN FETCH o.user " +
           "WHERE o.instrument.ticker = :ticker AND o.status IN ('PENDING','PARTIALLY_FILLED')")
    List<OrderEntity> findActiveByTicker(String ticker);

    @Query("SELECT o FROM OrderEntity o JOIN FETCH o.instrument JOIN FETCH o.user " +
           "WHERE o.status IN ('PENDING','PARTIALLY_FILLED') ORDER BY o.createdAt DESC")
    List<OrderEntity> findAllActive();

    @Query(value = "SELECT o FROM OrderEntity o JOIN FETCH o.instrument JOIN FETCH o.user " +
                   "ORDER BY o.createdAt DESC",
           countQuery = "SELECT COUNT(o) FROM OrderEntity o")
    Page<OrderEntity> findAllPaged(Pageable pageable);

    Page<OrderEntity> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to, Pageable pageable);

    @Query("SELECT o FROM OrderEntity o JOIN FETCH o.instrument JOIN FETCH o.user " +
           "WHERE o.tradingAccount.id = :accountId ORDER BY o.createdAt DESC")
    List<OrderEntity> findByTradingAccountId(Long accountId);

    List<OrderEntity> findByUserIdAndStatus(Long userId, String status);
}
