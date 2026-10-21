package com.trading.engine;

import com.trading.model.Order;
import com.trading.model.OrderType;
import com.trading.model.Side;
import com.trading.model.Trade;
import com.trading.risk.MarketContext;
import com.trading.risk.OrderValidator;
import com.trading.risk.SlippageModel;
import com.trading.risk.ValidationResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * 撮合引擎：按「价格优先 + 时间优先」撮合买卖双方。
 * 支持限价单、市价单（簿深不足时用滑点模型模拟更深层流动性）、
 * 止损单（市价触及触发价后自动转市价单）、IOC（未成交部分立即取消）、冰山单（分批可见）。
 *
 * <p>并发模型：GUI 线程、Timer 线程、AI 托管线程可能同时下单，
 * 因此所有会修改订单簿状态的入口（submitOrder / cancelOrder / triggerStopOrders）
 * 都用同一把锁串行化，保证「检查最优价→成交→减仓」是原子的。
 *
 * <p>扩展点（默认关闭，注入后才生效）：
 * <ul>
 *   <li>{@link OrderValidator}：撮合前的风控拦截（涨跌停 / 熔断）</li>
 *   <li>{@link ShortSellPolicy}：做空权限与保证金校验</li>
 * </ul>
 */
public class MatchingEngine {

    /** 虚拟流动性提供方名称：簿深耗尽时的成交对手 */
    public static final String LIQUIDITY_POOL = "LIQUIDITY_POOL";

    private final OrderBook orderBook = new OrderBook();
    private final List<Order> stopOrders = new ArrayList<>();
    private final SlippageModel slippage;
    private final Object bookLock = new Object();

    private volatile double lastPrice = Double.NaN;
    private volatile int virtualUsed;   // 本期内已消耗的虚拟深度（每期重置）

    private volatile OrderValidator validator;
    private volatile ShortSellPolicy shortSellPolicy;
    private int rejectedCount;

    public MatchingEngine() {
        this(new SlippageModel(0.10));
    }

    public MatchingEngine(SlippageModel slippage) {
        this.slippage = slippage;
    }

    /** 注入风控拦截器（涨跌停 / 熔断），null 表示不启用风控 */
    public void setValidator(OrderValidator validator) {
        this.validator = validator;
    }

    /** 注入做空策略，null 表示不做做空校验 */
    public void setShortSellPolicy(ShortSellPolicy policy) {
        this.shortSellPolicy = policy;
    }

    /** 风控拒单计数（GUI 风控面板展示用） */
    public int getRejectedCount() {
        return rejectedCount;
    }

    /** 更新最新市价（每期由仿真系统调用，用于止损触发与滑点计算） */
    public void setLastPrice(double price) {
        this.lastPrice = price;
    }

    /** 重置本期的虚拟流动性深度（每期开始交易前调用） */
    public void resetVirtualDepth() {
        synchronized (bookLock) {
            virtualUsed = 0;
        }
    }

    public double getLastPrice() {
        return lastPrice;
    }

    /**
     * 提交订单并撮合。
     * 主动方吃掉对手盘挂单时按挂单价成交（限价单获得价格改善）；
     * 市价单在簿深不足时进入有限容量的虚拟深度逐层成交（滑点模型），
     * 虚拟深度耗尽后剩余部分取消（模拟流动性不足）。
     *
     * @return 本次产生的成交；被风控拒绝时返回空列表
     */
    public List<Trade> submitOrder(Order order) {
        return submitOrder(order, -1, System.currentTimeMillis());
    }

    /**
     * @param referencePrice 参考价（初始价），用于风控上下文；传负数表示不启用
     * @param nowMillis      当前时间戳
     */
    public List<Trade> submitOrder(Order order, double referencePrice, long nowMillis) {
        return submitOrder(order, referencePrice, nowMillis, 0);
    }

    /**
     * @param tick 当前仿真周期，供风控规则（熔断）判断用
     */
    public List<Trade> submitOrder(Order order, double referencePrice, long nowMillis, int tick) {
        return submitOrderInternal(order, referencePrice, nowMillis, tick);
    }

    private List<Trade> submitOrderInternal(Order order, double referencePrice, long nowMillis, int tick) {
        // 止损单先挂起，等待市价触发
        if (order.getType() == OrderType.STOP) {
            synchronized (bookLock) {
                stopOrders.add(order);
            }
            return Collections.emptyList();
        }

        OrderValidator currentValidator = validator;
        if (currentValidator != null && referencePrice > 0) {
            MarketContext context = new MarketContext(order.getSymbol(), lastPrice, referencePrice, tick, nowMillis);
            ValidationResult result = currentValidator.validate(order, context);
            if (!result.isAccepted()) {
                rejectedCount++;
                return Collections.emptyList();
            }
        }

        ShortSellPolicy policy = shortSellPolicy;
        if (policy != null && order.getSide() == Side.SELL) {
            double reference = Double.isNaN(order.getPrice()) || order.getPrice() <= 0 ? lastPrice : order.getPrice();
            ValidationResult result = policy.checkSell(order.getTraderName(), order.getSymbol(),
                    reference, order.getQuantity());
            if (!result.isAccepted()) {
                rejectedCount++;
                return Collections.emptyList();
            }
        }

        if (Double.isNaN(lastPrice) && Double.isFinite(order.getPrice()) && order.getPrice() > 0) {
            lastPrice = order.getPrice();
        }

        List<Trade> trades = new ArrayList<>();
        boolean isMarket = order.getType() == OrderType.MARKET;
        boolean isBuy = order.getSide() == Side.BUY;
        String aggressorReason = order.isStopTriggered()
                ? "stop-loss triggered, converted to market order"
                : "aggressor crossed the spread";

        synchronized (bookLock) {
            while (order.getQuantity() > 0) {
                Order best = isBuy ? orderBook.getBestAsk() : orderBook.getBestBid();
                boolean priceCross = best != null && (isMarket
                        || (isBuy ? order.getPrice() >= best.getPrice() : order.getPrice() <= best.getPrice()));

                if (priceCross) {
                    int quantity = Math.min(order.getQuantity(), best.effectiveQuantity());
                    double tradePrice = best.getPrice();
                    Trade trade = isBuy
                            ? new Trade(order, best, tradePrice, quantity, aggressorReason)
                            : new Trade(best, order, tradePrice, quantity, aggressorReason);
                    trades.add(trade);
                    order.reduceQuantity(quantity);
                    best.reduceQuantity(quantity);
                    lastPrice = tradePrice;
                    if (best.getQuantity() == 0) {
                        orderBook.removeOrder(best);
                    }
                    // 冰山单：effectiveQuantity 自动补充下一个切片，无需额外处理
                    if (!isBuy && shortSellPolicy != null) {
                        shortSellPolicy.onSellFill(order.getTraderName(), order.getSymbol(), tradePrice, quantity);
                    }
                } else if (isMarket && virtualUsed < slippage.virtualCapacity()) {
                    // 簿深不足：进入有限容量的虚拟深度，逐层恶化成交价（按 0.01 最小变动价位取整）
                    int level = slippage.levelFor(virtualUsed);
                    int quantity = Math.min(order.getQuantity(), slippage.remainingInLevel(virtualUsed));
                    double execPrice = Math.round(slippage.priceAfterLevels(lastPrice, level, order.getSide()) * 100) / 100.0;
                    Order counterparty = new Order(
                            order.getSymbol(),
                            isBuy ? Side.SELL : Side.BUY,
                            OrderType.MARKET,
                            execPrice,
                            quantity,
                            LIQUIDITY_POOL);
                    Trade trade = isBuy
                            ? new Trade(order, counterparty, execPrice, quantity,
                                    "slippage: order book depth exhausted, virtual level " + level)
                            : new Trade(counterparty, order, execPrice, quantity,
                                    "slippage: order book depth exhausted, virtual level " + level);
                    trades.add(trade);
                    order.reduceQuantity(quantity);
                    virtualUsed += quantity;
                    // 注意：虚拟成交不更新 lastPrice——价格阶梯锚定本期市场价格，
                    // 避免基础价漂移导致重复加价
                } else {
                    // 限价单不交叉或流动性耗尽（市价单剩余取消）
                    break;
                }
            }

            // 剩余部分：限价单挂入订单簿；IOC 与市价单直接取消
            if (order.getQuantity() > 0 && !isMarket && order.getType() != OrderType.IOC) {
                orderBook.addOrder(order);
            }
        }
        return trades;
    }

    /**
     * 检查所有止损单，市价触及触发价后转为市价单执行。
     *
     * @param marketPrice     当前市价
     * @param referencePrice  参考价（用于风控上下文），传负数表示不启用
     */
    public List<Trade> triggerStopOrders(double marketPrice, double referencePrice, long nowMillis) {
        List<Trade> triggeredTrades = new ArrayList<>();
        List<Order> toTrigger = new ArrayList<>();
        synchronized (bookLock) {
            Iterator<Order> iterator = stopOrders.iterator();
            while (iterator.hasNext()) {
                Order stopOrder = iterator.next();
                boolean triggered = (stopOrder.getSide() == Side.SELL && marketPrice <= stopOrder.getStopPrice())
                        || (stopOrder.getSide() == Side.BUY && marketPrice >= stopOrder.getStopPrice());
                if (triggered) {
                    iterator.remove();
                    toTrigger.add(stopOrder);
                }
            }
        }
        for (Order stopOrder : toTrigger) {
            Order marketOrder = new Order(
                    stopOrder.getSymbol(),
                    stopOrder.getSide(),
                    OrderType.MARKET,
                    stopOrder.getStopPrice(),
                    stopOrder.getQuantity(),
                    stopOrder.getTraderName());
            marketOrder.markStopTriggered();
            triggeredTrades.addAll(submitOrder(marketOrder, referencePrice, nowMillis));
        }
        return triggeredTrades;
    }

    /** 兼容旧签名（无风控上下文） */
    public List<Trade> triggerStopOrders(double marketPrice) {
        return triggerStopOrders(marketPrice, -1, System.currentTimeMillis());
    }

    /** 撤单（支持挂单与待触发止损单） */
    public boolean cancelOrder(Order order) {
        if (order == null) {
            return false;
        }
        synchronized (bookLock) {
            if (order.getType() == OrderType.STOP) {
                return stopOrders.remove(order);
            }
            return orderBook.removeOrder(order);
        }
    }

    public OrderBook getOrderBook() {
        return orderBook;
    }

    /** 某一交易员当前的全部挂单（AI 托管自动撤单用） */
    public List<Order> openOrdersOf(String traderName) {
        synchronized (bookLock) {
            return orderBook.ordersOf(traderName);
        }
    }
}
