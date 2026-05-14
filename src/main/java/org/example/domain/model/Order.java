package org.example.domain.model;

import org.example.domain.model.enums.OrderStatus;
import org.example.domain.model.enums.Side;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class Order {

    private final String id;
    private final String userId;
    private final String instrumentTicker;
    private Side sideOfOrder;
    private BigDecimal price;
    private int quantity;
    private OrderStatus status;
    private final LocalDateTime createdAt;

    private Order(Builder builder) {
        this.id             = builder.id;
        this.userId         = builder.userId;
        this.instrumentTicker = builder.instrumentTicker;
        this.sideOfOrder    = builder.side;
        this.price          = builder.price;
        this.quantity       = builder.quantity;
        this.status         = OrderStatus.PENDING;
        this.createdAt      = LocalDateTime.now();
    }

    public String getId()                  { return id; }
    public String getUserId()              { return userId; }
    public String getInstrumentTicker()    { return instrumentTicker; }
    public Side getSideOfOrder()           { return sideOfOrder; }
    public BigDecimal getPrice()           { return price; }
    public int getQuantity()               { return quantity; }
    public OrderStatus getStatus()         { return status; }
    public LocalDateTime getCreatedAt()    { return createdAt; }

    public void setQuantity(int quantity)       { this.quantity = quantity; }
    public void setStatus(OrderStatus status)   { this.status  = status;   }

    public static class Builder {
        private String id            = UUID.randomUUID().toString();
        private String userId        = "SYSTEM";
        private String instrumentTicker = "SBER";
        private Side   side          = Side.BUY;
        private BigDecimal price     = BigDecimal.valueOf(100 + (int)(Math.random() * 50));
        private int    quantity      = 1 + (int)(Math.random() * 100);

        public Builder addId(String id)                        { this.id = id;               return this; }
        public Builder addUserId(String userId)                { this.userId = userId;       return this; }
        public Builder addTicker(String ticker)                { this.instrumentTicker = ticker; return this; }
        public Builder addSide(Side side)                      { this.side = side;           return this; }
        public Builder addPrice(BigDecimal price)              { this.price = price;         return this; }
        public Builder addQuantity(int quantity)               { this.quantity = quantity;   return this; }

        public Order build() { return new Order(this); }
    }

    @Override
    public String toString() {
        return "Order{id='%s', side=%s, price=%s, qty=%d, status=%s}"
                .formatted(id, sideOfOrder, price, quantity, status);
    }
}
