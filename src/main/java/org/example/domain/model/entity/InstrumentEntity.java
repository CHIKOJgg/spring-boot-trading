package org.example.domain.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "instruments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InstrumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String ticker;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "instrument_type", nullable = false, length = 50)
    private String instrumentType;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "RUB";

    @Column(name = "lot_size", nullable = false)
    @Builder.Default
    private Integer lotSize = 1;

    @Column(name = "tick_size", nullable = false, precision = 20, scale = 8)
    @Builder.Default
    private BigDecimal tickSize = new BigDecimal("0.01");

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
