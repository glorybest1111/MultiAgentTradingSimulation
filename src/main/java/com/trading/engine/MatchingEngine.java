package com.trading.engine;

import com.trading.model.Order;
import com.trading.model.OrderType;
import com.trading.model.Side;
import com.trading.model.Trade;
import com.trading.risk.SlippageModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * 撮合引擎：按「价格优先 + 时间优先」撮合买卖双方。
 * 支持限价单、市价单（簿深不足时用滑点模型模拟更深层流动性）、
 * 止损单（市价触及触发价后自动转市价单）、IOC（未成交部分立即取消）、冰山单（分批可见）。
 */
public class MatchingEngine {

    /** 虚拟流动性提供方名称：簿深耗尽时的成交对手 */
    public static final String LIQUIDITY_POOL = "LIQUIDITY_POOL";

    private final OrderBook orderBook = new OrderBook();
    private final List<Order> stopOrders = new ArrayList<>();
    private final SlippageModel slippage;
    private double lastPrice = Double.NaN;
    private int virtualUsed;   // 本期内已消耗的虚拟深度（每期重置）

    public MatchingEngine() {
        this(new SlippageModel(0.10));
    }

    public MatchingEngine(SlippageModel slippage) {
        this.slippage = slippage;
    }

    /** 更新最新市价（每期由仿真系统调用，用于止损触发与滑点计算） */
    public void setLastPrice(double price) {
        this.lastPrice = price;
    }

    /** 重置本期的虚拟流动性深度（每期开始交易前调用） */
    public void resetVirtualDepth() {
        virtualUsed = 0;
    }

    public double getLastPrice() {
        return lastPrice;
    }

    /**
     * 提交订单并撮合。
     * 主动方吃掉对手盘挂单时按挂单价成交（限价单获得价格改善）；
     * 市价单在簿深不足时进入有限容量的虚拟深度逐层成交（滑点模型），
     * 虚拟深度耗尽后剩余部分取消（模拟流动性不足）。
     */
    public List<Trade> submitOrder(Order order) {
        // 止损单先挂起，等待市价触发
        if (order.getType() == OrderType.STOP) {
            stopOrders.add(order);
            return Collections.emptyList();
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
        return trades;
    }

    /** 检查所有止损单，市价触及触发价后转为市价单执行 */
    public List<Trade> triggerStopOrders(double marketPrice) {
        List<Trade> triggeredTrades = new ArrayList<>();
        Iterator<Order> iterator = stopOrders.iterator();
        while (iterator.hasNext()) {
            Order stopOrder = iterator.next();
            boolean triggered = (stopOrder.getSide() == Side.SELL && marketPrice <= stopOrder.getStopPrice())
                    || (stopOrder.getSide() == Side.BUY && marketPrice >= stopOrder.getStopPrice());
            if (triggered) {
                iterator.remove();
                Order marketOrder = new Order(
                        stopOrder.getSymbol(),
                        stopOrder.getSide(),
                        OrderType.MARKET,
                        stopOrder.getStopPrice(),
                        stopOrder.getQuantity(),
                        stopOrder.getTraderName());
                marketOrder.markStopTriggered();
                triggeredTrades.addAll(submitOrder(marketOrder));
            }
        }
        return triggeredTrades;
    }

    /** 撤单（支持挂单与待触发止损单） */
    public boolean cancelOrder(Order order) {
        if (order.getType() == OrderType.STOP) {
            return stopOrders.remove(order);
        }
        return orderBook.removeOrder(order);
    }

    public OrderBook getOrderBook() {
        return orderBook;
    }

}
