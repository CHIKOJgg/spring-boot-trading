package org.example.domain.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "trading_accounts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TradingAccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "account_number", nullable = false, unique = true, length = 30)
    private String accountNumber;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "RUB";

    @Column(name = "cash_balance", nullable = false, precision = 20, scale = 4)
    @Builder.Default
    private BigDecimal cashBalance = BigDecimal.ZERO;

    @Column(name = "frozen_balance", nullable = false, precision = 20, scale = 4)
    @Builder.Default
    private BigDecimal frozenBalance = BigDecimal.ZERO;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "tradingAccount")
    private List<OrderEntity> orders;

    @OneToMany(mappedBy = "tradingAccount")
    private List<TradingPositionEntity> positions;

    public BigDecimal getAvailableBalance() {
        return cashBalance.subtract(frozenBalance);
    }
}
