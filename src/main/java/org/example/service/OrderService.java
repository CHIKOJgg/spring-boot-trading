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

        BigDecimal orderValue = request.price().multiply(BigDecimal.valueOf(request.quantity()));

        if ("BUY".equals(request.side())) {
            if (account.getAvailableBalance().compareTo(orderValue) < 0)
                throw new InsufficientFundsException(
                        "Insufficient funds. Required: " + orderValue
                        + ", Available: " + account.getAvailableBalance());
            account.setFrozenBalance(account.getFrozenBalance().add(orderValue));

        } else { // SELL
            TradingPositionEntity position = positionRepo
                    .findByTradingAccountIdAndInstrumentId(account.getId(), instrument.getId())
                    .orElse(null);
            int available = (position == null) ? 0 : position.getAvailableQuantity();
            if (available < request.quantity())
                throw new InsufficientFundsException(
                        "Insufficient position in " + request.ticker()
                        + ". Required: " + request.quantity()
                        + ", Available: " + available
                        + ". You need to BUY this instrument first.");
            if (position != null) {
                position.setFrozenQuantity(position.getFrozenQuantity() + request.quantity());
                positionRepo.save(position);
            }
        }
        tradingAccountRepo.save(account);

        String orderId = UUID.randomUUID().toString();
        OrderEntity entity = OrderEntity.builder()
                .id(orderId).user(user).tradingAccount(account).instrument(instrument)
                .side(request.side()).price(request.price())
                .quantity(request.quantity()).remainingQty(request.quantity())
                .timeInForce(request.timeInForce()).createdAt(LocalDateTime.now())
                .build();
        orderRepository.save(entity);

        Order domainOrder = new Order.Builder()
                .addId(orderId).addUserId(user.getId().toString())
                .addTicker(request.ticker()).addSide(Side.valueOf(request.side()))
                .addPrice(request.price()).addQuantity(request.quantity())
                .addTimeInForce(request.timeInForce()).build();

        List<TradeResult> trades = matchingEngine.placeLimitOrder(domainOrder);
        for (TradeResult tr : trades) {
            persistAndSettle(tr, instrument);
        }

        entity.setStatus(domainOrder.getStatus().name());
        entity.setRemainingQty(domainOrder.getQuantity());
        orderRepository.save(entity);

        // BUG FIX #2: IOC/FOK orders that are cancelled after partial/no fill
        // must release the frozen balance for the unmatched portion.
        // Previously the frozen funds were locked forever when IOC/FOK was cancelled.
        if ("CANCELLED".equals(entity.getStatus()) && entity.getRemainingQty() > 0) {
            releaseFrozen(entity);
        }

        auditService.logSuccess(username, "PLACE_ORDER", "ORDER", orderId);
        notificationService.notify(user, "ORDER_PLACED", "Order placed",
                request.side() + " " + request.quantity() + " " + request.ticker()
                + " @" + request.price());
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

        matchingEngine.cancelOrder(entity.getInstrument().getTicker(), orderId);
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
    //  Queries — BUG FIX: all read methods now @Transactional(readOnly=true)
    //  so the Hibernate session remains open while toOrderResponse() accesses
    //  lazy associations. Combined with JOIN FETCH in the repository, this
    //  eliminates all LazyInitializationExceptions.
    // -------------------------------------------------------

    @Transactional(readOnly = true)
    public List<OrderResponse> getActiveOrders(String username) {
        UserEntity user = findUser(username);
        return orderRepository.findActiveByUserId(user.getId())
                .stream().map(this::toOrderResponse).toList();
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrderHistory(String username, Pageable pageable) {
        UserEntity user = findUser(username);
        return orderRepository.findByUserId(user.getId(), pageable)
                .map(this::toOrderResponse);
    }

    @Transactional(readOnly = true)
    public Page<TradeResponse> getTradeHistory(String username, Pageable pageable) {
        UserEntity user = findUser(username);
        return tradeRepository.findByUserIdPaged(user.getId(), pageable)
                .map(this::toTradeResponse);
    }

    @Transactional(readOnly = true)
    public List<PositionResponse> getPositions(String username, Long tradingAccountId) {
        UserEntity user = findUser(username);
        TradingAccountEntity account = tradingAccountRepo.findById(tradingAccountId)
                .orElseThrow(() -> new AccountNotFoundException("Trading account not found"));
        if (!account.getUser().getId().equals(user.getId()))
            throw new AccountNotFoundException("Access denied");
        return positionRepo.findByTradingAccountId(tradingAccountId)
                .stream().map(this::toPositionResponse).toList();
    }

    // Admin — view all orders
    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(Pageable pageable) {
        return orderRepository.findAllPaged(pageable).map(this::toOrderResponse);
    }

    // -------------------------------------------------------
    //  Settlement
    // -------------------------------------------------------

    /**
     * BUG FIX #1 helper: syncs the resting (counterparty) order's status back to the DB.
     * The incoming order's status is updated by placeOrder() after matching.
     * The resting order is updated here, inside the settlement transaction.
     */
    private void updateRestingOrderStatus(TradeResult tr,
                                          OrderEntity buyOrder, OrderEntity sellOrder) {
        // Determine which order was RESTING (was in the book before this trade)
        // and which was INCOMING (just placed). The incoming order is updated by
        // placeOrder() itself; we only need to handle the resting one here.
        // We identify the resting order as the one whose ID is NOT the incoming
        // order that triggered this settlement — but since persistAndSettle is
        // called for every trade, we update BOTH sides defensively.
        updateOrderInDb(buyOrder,  tr.quantity());
        updateOrderInDb(sellOrder, tr.quantity());
    }

    private void updateOrderInDb(OrderEntity order, int executedQty) {
        if (order == null) return;
        int newRemaining = Math.max(0, order.getRemainingQty() - executedQty);
        order.setRemainingQty(newRemaining);
        if (newRemaining == 0) {
            order.setStatus("FILLED");
        } else if (newRemaining < order.getQuantity()) {
            order.setStatus("PARTIALLY_FILLED");
        }
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
    }

        private void persistAndSettle(TradeResult tr, InstrumentEntity instrument) {
        OrderEntity buyOrder  = orderRepository.findById(tr.buyOrderId()).orElse(null);
        OrderEntity sellOrder = orderRepository.findById(tr.sellOrderId()).orElse(null);
        if (buyOrder == null || sellOrder == null) return;

        // BUG FIX #1: Update the RESTING order's status in the DB.
        // Previously only the INCOMING order entity was saved after matching.
        // The resting order (counterparty) stayed PENDING in the database forever,
        // showing as an active order in the UI even though it had been filled.
        updateRestingOrderStatus(tr, buyOrder, sellOrder);

        TradeEntity trade = TradeEntity.builder()
                .id(UUID.randomUUID().toString())
                .buyOrder(buyOrder).sellOrder(sellOrder).instrument(instrument)
                .price(tr.price()).quantity(tr.quantity()).tradedAt(tr.tradedAt())
                .buyer(buyOrder.getUser()).seller(sellOrder.getUser())
                .build();
        tradeRepository.save(trade);

        BigDecimal executionValue = tr.price().multiply(BigDecimal.valueOf(tr.quantity()));

        // BUG FIX: double-deduction — previously frozenBalance was reduced by executionValue
        // (the trade price × qty). But frozenBalance was originally reserved at the ORDER's
        // limit price. If execution price < limit price (price improvement), the difference
        // stayed locked in frozenBalance forever.
        // Correct logic:
        //   frozenRelease = buyOrder.getPrice() × qty  (release the full reservation for these lots)
        //   cashCost      = executionPrice × qty        (actual money paid to seller)
        //   availableBalance increases by (limitPrice - executionPrice) × qty (price improvement benefit)
        BigDecimal frozenRelease = buyOrder.getPrice().multiply(BigDecimal.valueOf(tr.quantity()));

        // Settle buyer: deduct actual cost + release original frozen reservation
        TradingAccountEntity buyerAccount = buyOrder.getTradingAccount();
        buyerAccount.setFrozenBalance(buyerAccount.getFrozenBalance()
                .subtract(frozenRelease).max(BigDecimal.ZERO));
        buyerAccount.setCashBalance(buyerAccount.getCashBalance()
                .subtract(executionValue).max(BigDecimal.ZERO));
        tradingAccountRepo.save(buyerAccount);
        updatePosition(buyerAccount, instrument, tr.quantity(), tr.price(), true);

        // Settle seller: add cash + release frozen position
        TradingAccountEntity sellerAccount = sellOrder.getTradingAccount();
        sellerAccount.setCashBalance(sellerAccount.getCashBalance().add(executionValue));
        tradingAccountRepo.save(sellerAccount);
        updatePosition(sellerAccount, instrument, tr.quantity(), tr.price(), false);

        notificationService.notify(buyOrder.getUser(), "TRADE_EXECUTED", "Trade executed",
                "Bought " + tr.quantity() + " " + instrument.getTicker() + " @" + tr.price() + " (limit: " + buyOrder.getPrice() + ")");
        notificationService.notify(sellOrder.getUser(), "TRADE_EXECUTED", "Trade executed",
                "Sold " + tr.quantity() + " " + instrument.getTicker() + " @" + tr.price());

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
            BigDecimal totalCost = (pos.getAvgCost() != null
                    ? pos.getAvgCost().multiply(BigDecimal.valueOf(pos.getQuantity()))
                    : BigDecimal.ZERO).add(price.multiply(BigDecimal.valueOf(qty)));
            int newQty = pos.getQuantity() + qty;
            pos.setAvgCost(newQty > 0
                    ? totalCost.divide(BigDecimal.valueOf(newQty), 4, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
            pos.setQuantity(newQty);
        } else {
            pos.setQuantity(pos.getQuantity() - qty);
            pos.setFrozenQuantity(Math.max(0, pos.getFrozenQuantity() - qty));
        }
        pos.setUpdatedAt(LocalDateTime.now());
        positionRepo.save(pos);
    }

    private void releaseFrozen(OrderEntity entity) {
        TradingAccountEntity account = tradingAccountRepo
                .findByIdForUpdate(entity.getTradingAccount().getId()).orElse(null);
        if (account == null) return;
        if ("BUY".equals(entity.getSide())) {
            BigDecimal release = entity.getPrice()
                    .multiply(BigDecimal.valueOf(entity.getRemainingQty()));
            account.setFrozenBalance(account.getFrozenBalance()
                    .subtract(release).max(BigDecimal.ZERO));
        } else {
            positionRepo.findByTradingAccountIdAndInstrumentId(
                    account.getId(), entity.getInstrument().getId())
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
            messagingTemplate.convertAndSend("/topic/orderbook/" + ticker,
                    buildOrderBookSnapshot(ticker, book));
        } catch (Exception ignored) {}
    }

    private OrderBookResponse buildOrderBookSnapshot(String ticker,
            org.example.domain.service.OrderBook book) {
        List<OrderBookLevelResponse> bids = book.getBids().entrySet().stream().limit(20)
                .map(e -> new OrderBookLevelResponse(e.getKey(),
                        e.getValue().stream().mapToInt(Order::getQuantity).sum(),
                        e.getValue().size())).toList();
        List<OrderBookLevelResponse> asks = book.getAsks().entrySet().stream().limit(20)
                .map(e -> new OrderBookLevelResponse(e.getKey(),
                        e.getValue().stream().mapToInt(Order::getQuantity).sum(),
                        e.getValue().size())).toList();
        return new OrderBookResponse(ticker, bids, asks,
                book.getBidAskSpread().orElse(null), LocalDateTime.now());
    }

    // -------------------------------------------------------
    //  Mappers
    // -------------------------------------------------------

    OrderResponse toOrderResponse(OrderEntity o) {
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
