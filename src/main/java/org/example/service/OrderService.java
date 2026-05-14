package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.domain.model.Order;
import org.example.domain.model.TradeResult;
import org.example.domain.model.entity.*;
import org.example.domain.model.enums.Side;
import org.example.domain.service.MatchingEngine;
import org.example.dto.request.OrderRequest.*;
import org.example.dto.response.ApiResponse.*;
import org.example.exception.GlobalExceptionHandler.*;
import org.example.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;
    private final TradingAccountRepository tradingAccountRepo;
    private final TradingPositionRepository positionRepo;
    private final InstrumentRepository instrumentRepo;
    private final UserRepository userRepository;
    private final MatchingEngine matchingEngine;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    // -------------------------------------------------------
    //  Place order
    // -------------------------------------------------------

    @Transactional
    public OrderResponse placeOrder(String username, PlaceOrderRequest request) {
        UserEntity user = findUser(username);
        InstrumentEntity instrument = instrumentRepo.findByTicker(request.ticker())
                .orElseThrow(() -> new InstrumentNotFoundException(request.ticker()));

        if (!instrument.getIsActive())
            throw new TradingException("Instrument " + request.ticker() + " is not active");

        TradingAccountEntity account = tradingAccountRepo.findByIdForUpdate(request.tradingAccountId())
                .orElseThrow(() -> new AccountNotFoundException("Trading account not found"));

        if (!account.getUser().getId().equals(user.getId()))
            throw new AccountNotFoundException("Trading account not found");
        if (!"ACTIVE".equals(account.getStatus()))
            throw new AccountFrozenException("Trading account is not active");

        // risk check
        BigDecimal orderValue = request.price().multiply(BigDecimal.valueOf(request.quantity()));

        if (request.side().equals("BUY")) {
            if (account.getAvailableBalance().compareTo(orderValue) < 0)
                throw new InsufficientFundsException("Insufficient trading balance. Required: "
                        + orderValue + ", Available: " + account.getAvailableBalance());
            // freeze funds
            account.setFrozenBalance(account.getFrozenBalance().add(orderValue));
        } else {
            // SELL: check position
            TradingPositionEntity position = positionRepo
                    .findByTradingAccountIdAndInstrumentId(account.getId(), instrument.getId())
                    .orElse(null);
            int available = (position == null) ? 0 : position.getAvailableQuantity();
            if (available < request.quantity())
                throw new InsufficientFundsException("Insufficient position. Required: "
                        + request.quantity() + ", Available: " + available);
            // freeze position
            if (position != null) {
                position.setFrozenQuantity(position.getFrozenQuantity() + request.quantity());
                positionRepo.save(position);
            }
        }
        tradingAccountRepo.save(account);

        // persist order
        String orderId = UUID.randomUUID().toString();
        OrderEntity entity = OrderEntity.builder()
                .id(orderId)
                .user(user)
                .tradingAccount(account)
                .instrument(instrument)
                .side(request.side())
                .price(request.price())
                .quantity(request.quantity())
                .remainingQty(request.quantity())
                .timeInForce(request.timeInForce())
                .createdAt(LocalDateTime.now())
                .build();
        orderRepository.save(entity);

        // submit to matching engine
        Order domainOrder = new Order.Builder()
                .addId(orderId)
                .addUserId(user.getId().toString())
                .addTicker(request.ticker())
                .addSide(Side.valueOf(request.side()))
                .addPrice(request.price())
                .addQuantity(request.quantity())
                .build();

        List<TradeResult> trades = matchingEngine.placeLimitOrder(domainOrder);

        // persist trades and settle
        for (TradeResult tr : trades) {
            persistAndSettle(tr, instrument);
        }

        // update order status in DB
        entity.setStatus(domainOrder.getStatus().name());
        entity.setRemainingQty(domainOrder.getQuantity());
        orderRepository.save(entity);

        auditService.logSuccess(username, "PLACE_ORDER", "ORDER", orderId);
        notificationService.notify(user, "ORDER_PLACED", "Order placed",
                request.side() + " " + request.quantity() + " " + request.ticker() + " @" + request.price());

        // broadcast updated order book
        broadcastOrderBook(request.ticker());

        return toOrderResponse(entity);
    }

    // -------------------------------------------------------
    //  Cancel order
    // -------------------------------------------------------

    @Transactional
    public OrderResponse cancelOrder(String username, String orderId) {
        UserEntity user = findUser(username);
        OrderEntity entity = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!entity.getUser().getId().equals(user.getId()))
            throw new OrderNotFoundException(orderId);

        if (!List.of("PENDING", "PARTIALLY_FILLED").contains(entity.getStatus()))
            throw new TradingException("Order cannot be cancelled in status: " + entity.getStatus());

        // cancel in matching engine
        matchingEngine.cancelOrder(entity.getInstrument().getTicker(), orderId);

        // release frozen funds/position
        releaseFrozen(entity);

        entity.setStatus("CANCELLED");
        orderRepository.save(entity);

        auditService.logSuccess(username, "CANCEL_ORDER", "ORDER", orderId);
        notificationService.notify(user, "ORDER_CANCELLED", "Order cancelled",
                "Order " + orderId.substring(0, 8) + "... has been cancelled");
        broadcastOrderBook(entity.getInstrument().getTicker());

        return toOrderResponse(entity);
    }

    // -------------------------------------------------------
    //  Queries
    // -------------------------------------------------------

    public List<OrderResponse> getActiveOrders(String username) {
        UserEntity user = findUser(username);
        return orderRepository.findActiveByUserId(user.getId())
                .stream().map(this::toOrderResponse).toList();
    }

    public Page<OrderResponse> getOrderHistory(String username, Pageable pageable) {
        UserEntity user = findUser(username);
        return orderRepository.findByUserId(user.getId(), pageable).map(this::toOrderResponse);
    }

    public Page<TradeResponse> getTradeHistory(String username, Pageable pageable) {
        UserEntity user = findUser(username);
        return tradeRepository.findByUserIdPaged(user.getId(), pageable).map(this::toTradeResponse);
    }

    public List<PositionResponse> getPositions(String username, Long tradingAccountId) {
        UserEntity user = findUser(username);
        TradingAccountEntity account = tradingAccountRepo.findById(tradingAccountId)
                .orElseThrow(() -> new AccountNotFoundException("Trading account not found"));
        if (!account.getUser().getId().equals(user.getId()))
            throw new AccountNotFoundException("Access denied");
        return positionRepo.findByTradingAccountId(tradingAccountId)
                .stream().map(this::toPositionResponse).toList();
    }

    // -------------------------------------------------------
    //  Settlement
    // -------------------------------------------------------

    private void persistAndSettle(TradeResult tr, InstrumentEntity instrument) {
        OrderEntity buyOrder  = orderRepository.findById(tr.buyOrderId()).orElse(null);
        OrderEntity sellOrder = orderRepository.findById(tr.sellOrderId()).orElse(null);
        if (buyOrder == null || sellOrder == null) return;

        // persist trade
        TradeEntity trade = TradeEntity.builder()
                .id(UUID.randomUUID().toString())
                .buyOrder(buyOrder)
                .sellOrder(sellOrder)
                .instrument(instrument)
                .price(tr.price())
                .quantity(tr.quantity())
                .tradedAt(tr.tradedAt())
                .buyer(buyOrder.getUser())
                .seller(sellOrder.getUser())
                .build();
        tradeRepository.save(trade);

        BigDecimal totalValue = tr.price().multiply(BigDecimal.valueOf(tr.quantity()));

        // settle buyer: unfreeze funds, update position
        TradingAccountEntity buyerAccount = buyOrder.getTradingAccount();
        BigDecimal frozenRelease = tr.price().multiply(BigDecimal.valueOf(tr.quantity()));
        buyerAccount.setFrozenBalance(buyerAccount.getFrozenBalance().subtract(frozenRelease).max(BigDecimal.ZERO));
        buyerAccount.setCashBalance(buyerAccount.getCashBalance().subtract(totalValue).max(BigDecimal.ZERO));
        tradingAccountRepo.save(buyerAccount);
        updatePosition(buyerAccount, instrument, tr.quantity(), tr.price(), true);

        // settle seller: release frozen position, add cash
        TradingAccountEntity sellerAccount = sellOrder.getTradingAccount();
        sellerAccount.setCashBalance(sellerAccount.getCashBalance().add(totalValue));
        tradingAccountRepo.save(sellerAccount);
        updatePosition(sellerAccount, instrument, tr.quantity(), tr.price(), false);

        // notify
        notificationService.notify(buyOrder.getUser(), "TRADE_EXECUTED",
                "Trade executed", "Bought " + tr.quantity() + " " + instrument.getTicker() + " @" + tr.price());
        notificationService.notify(sellOrder.getUser(), "TRADE_EXECUTED",
                "Trade executed", "Sold " + tr.quantity() + " " + instrument.getTicker() + " @" + tr.price());

        // broadcast trade
        messagingTemplate.convertAndSend("/topic/trades/" + instrument.getTicker(),
                toTradeResponse(trade));
    }

    private void updatePosition(TradingAccountEntity account, InstrumentEntity instrument,
                                int qty, BigDecimal price, boolean isBuy) {
        TradingPositionEntity pos = positionRepo
                .findByTradingAccountIdAndInstrumentId(account.getId(), instrument.getId())
                .orElseGet(() -> {
                    TradingPositionEntity p = new TradingPositionEntity();
                    p.setTradingAccount(account);
                    p.setInstrument(instrument);
                    p.setQuantity(0);
                    p.setFrozenQuantity(0);
                    p.setUpdatedAt(LocalDateTime.now());
                    return p;
                });

        if (isBuy) {
            // update avg cost
            BigDecimal totalCost = (pos.getAvgCost() != null
                    ? pos.getAvgCost().multiply(BigDecimal.valueOf(pos.getQuantity()))
                    : BigDecimal.ZERO)
                    .add(price.multiply(BigDecimal.valueOf(qty)));
            int newQty = pos.getQuantity() + qty;
            pos.setAvgCost(newQty > 0 ? totalCost.divide(BigDecimal.valueOf(newQty), 4, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO);
            pos.setQuantity(newQty);
        } else {
            pos.setQuantity(pos.getQuantity() - qty);
            pos.setFrozenQuantity(Math.max(0, pos.getFrozenQuantity() - qty));
        }
        pos.setUpdatedAt(LocalDateTime.now());
        positionRepo.save(pos);
    }

    private void releaseFrozen(OrderEntity entity) {
        TradingAccountEntity account = tradingAccountRepo.findByIdForUpdate(
                entity.getTradingAccount().getId()).orElse(null);
        if (account == null) return;

        if ("BUY".equals(entity.getSide())) {
            BigDecimal release = entity.getPrice()
                    .multiply(BigDecimal.valueOf(entity.getRemainingQty()));
            account.setFrozenBalance(account.getFrozenBalance().subtract(release).max(BigDecimal.ZERO));
        } else {
            InstrumentEntity instrument = entity.getInstrument();
            positionRepo.findByTradingAccountIdAndInstrumentId(account.getId(), instrument.getId())
                    .ifPresent(p -> {
                        p.setFrozenQuantity(Math.max(0, p.getFrozenQuantity() - entity.getRemainingQty()));
                        positionRepo.save(p);
                    });
        }
        tradingAccountRepo.save(account);
    }

    private void broadcastOrderBook(String ticker) {
        try {
            org.example.domain.service.OrderBook book = matchingEngine.getBook(ticker);
            if (book == null) return;
            OrderBookResponse snapshot = buildOrderBookSnapshot(ticker, book);
            messagingTemplate.convertAndSend("/topic/orderbook/" + ticker, snapshot);
        } catch (Exception ignored) {}
    }

    private OrderBookResponse buildOrderBookSnapshot(String ticker,
            org.example.domain.service.OrderBook book) {
        List<OrderBookLevelResponse> bids = book.getBids().entrySet().stream()
                .limit(20)
                .map(e -> new OrderBookLevelResponse(e.getKey(),
                        e.getValue().stream().mapToInt(org.example.domain.model.Order::getQuantity).sum(),
                        e.getValue().size()))
                .toList();
        List<OrderBookLevelResponse> asks = book.getAsks().entrySet().stream()
                .limit(20)
                .map(e -> new OrderBookLevelResponse(e.getKey(),
                        e.getValue().stream().mapToInt(org.example.domain.model.Order::getQuantity).sum(),
                        e.getValue().size()))
                .toList();
        BigDecimal spread = book.getBidAskSpread().orElse(null);
        return new OrderBookResponse(ticker, bids, asks, spread, LocalDateTime.now());
    }

    // -------------------------------------------------------
    //  Mappers
    // -------------------------------------------------------

    private OrderResponse toOrderResponse(OrderEntity o) {
        return new OrderResponse(o.getId(), o.getInstrument().getTicker(),
                o.getSide(), o.getOrderType(), o.getPrice(), o.getQuantity(),
                o.getRemainingQty(), o.getQuantity() - o.getRemainingQty(),
                o.getStatus(), o.getTimeInForce(), o.getCreatedAt(), o.getUpdatedAt());
    }

    TradeResponse toTradeResponse(TradeEntity t) {
        return new TradeResponse(t.getId(), t.getInstrument().getTicker(),
                t.getBuyOrder().getId(), t.getSellOrder().getId(),
                t.getPrice(), t.getQuantity(), t.getTotalValue(), t.getTradedAt(),
                t.getBuyer().getUsername(), t.getSeller().getUsername());
    }

    private PositionResponse toPositionResponse(TradingPositionEntity p) {
        return new PositionResponse(p.getInstrument().getId(), p.getInstrument().getTicker(),
                p.getInstrument().getName(), p.getQuantity(), p.getFrozenQuantity(),
                p.getAvailableQuantity(), p.getAvgCost(), p.getUpdatedAt());
    }

    private UserEntity findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));
    }
}
