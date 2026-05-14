package org.example.domain.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trading_positions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TradingPositionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trading_account_id", nullable = false)
    private TradingAccountEntity tradingAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    private InstrumentEntity instrument;

    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 0;

    @Column(name = "frozen_quantity", nullable = false)
    @Builder.Default
    private Integer frozenQuantity = 0;

    @Column(name = "avg_cost", precision = 20, scale = 4)
    private BigDecimal avgCost;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Integer getAvailableQuantity() {
        return quantity - frozenQuantity;
    }
}
