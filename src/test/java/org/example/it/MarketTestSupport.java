package org.example.it;

import org.example.domain.model.entity.InstrumentEntity;
import org.example.domain.model.entity.OrderEntity;
import org.example.domain.model.entity.TradeEntity;
import org.example.domain.model.entity.TradingAccountEntity;
import org.example.domain.model.entity.UserEntity;
import org.example.repository.InstrumentRepository;
import org.example.repository.OrderRepository;
import org.example.repository.TradeRepository;
import org.example.repository.TradingAccountRepository;
import org.example.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public abstract class MarketTestSupport {

    @Autowired protected TradeRepository tradeRepository;
    @Autowired protected OrderRepository orderRepository;
    @Autowired protected TradingAccountRepository tradingAccountRepository;
    @Autowired protected InstrumentRepository instrumentRepository;
    @Autowired protected UserRepository userRepository;

    protected void resetData() {
        tradeRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        tradingAccountRepository.deleteAllInBatch();
        instrumentRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    protected InstrumentEntity saveInstrument(String ticker, String name, String instrumentType) {
        return instrumentRepository.save(InstrumentEntity.builder()
                .ticker(ticker)
                .name(name)
                .instrumentType(instrumentType)
                .currency("BYN")
                .lotSize(1)
                .tickSize(new BigDecimal("0.01"))
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build());
    }

    protected UserEntity saveUser(String username) {
        return userRepository.save(UserEntity.builder()
                .username(username)
                .email(username + "@example.com")
                .passwordHash("hash")
                .isActive(true)
                .isLocked(false)
                .build());
    }

    protected TradingAccountEntity saveTradingAccount(UserEntity user, String accountNumber) {
        return tradingAccountRepository.save(TradingAccountEntity.builder()
                .user(user)
                .accountNumber(accountNumber)
                .currency("BYN")
                .cashBalance(new BigDecimal("100000.00"))
                .frozenBalance(BigDecimal.ZERO)
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .build());
    }

    protected OrderEntity saveOrder(
            String id,
            UserEntity user,
            TradingAccountEntity account,
            InstrumentEntity instrument,
            String side,
            BigDecimal price,
            int quantity,
            int remainingQty,
            LocalDateTime createdAt) {
        return orderRepository.save(OrderEntity.builder()
                .id(id)
                .user(user)
                .tradingAccount(account)
                .instrument(instrument)
                .side(side)
                .orderType("LIMIT")
                .price(price)
                .quantity(quantity)
                .remainingQty(remainingQty)
                .status("FILLED")
                .timeInForce("GTC")
                .createdAt(createdAt)
                .build());
    }

    protected TradeEntity saveTrade(
            String id,
            OrderEntity buyOrder,
            OrderEntity sellOrder,
            InstrumentEntity instrument,
            UserEntity buyer,
            UserEntity seller,
            BigDecimal price,
            int quantity,
            LocalDateTime tradedAt) {
        return tradeRepository.save(TradeEntity.builder()
                .id(id)
                .buyOrder(buyOrder)
                .sellOrder(sellOrder)
                .instrument(instrument)
                .buyer(buyer)
                .seller(seller)
                .price(price)
                .quantity(quantity)
                .tradedAt(tradedAt)
                .status("EXECUTED")
                .build());
    }
}
