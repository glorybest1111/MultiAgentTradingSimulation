package com.trading.model;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 订单：交易系统的核心对象。
 * 支持五种类型：LIMIT（限价）、MARKET（市价）、STOP（止损）、IOC（立即成交否则取消）、ICEBERG（冰山）。
 */
public class Order {

    private static final AtomicLong ID_GENERATOR = new AtomicLong(1);

    private final long orderId;
    private final String symbol;
    private final Side side;
    private final OrderType type;
    private final double price;          // 限价单价格；市价单此字段仅作参考
    private int quantity;                // 剩余数量
    private final LocalDateTime timestamp;
    private final String traderName;      // 下单人，用于成交归属与 AI 解释

    private double stopPrice = Double.NaN;   // STOP 单触发价
    private int icebergSlice = -1;           // ICEBERG 单每次对市场可见的数量，-1 表示全部可见
    private boolean stopTriggered;           // 由止损单触发转换而来的市价单标记

    public Order(String symbol, Side side, OrderType type, double price, int quantity, String traderName) {
        this(ID_GENERATOR.getAndIncrement(), symbol, side, type, price, quantity, traderName);
    }

    public Order(long orderId, String symbol, Side side, OrderType type, double price, int quantity, String traderName) {
        this.orderId = orderId;
        this.symbol = symbol;
        this.side = side;
        this.type = type;
        this.price = price;
        this.quantity = quantity;
        this.traderName = traderName;
        this.timestamp = LocalDateTime.now();
    }

    /** 创建止损单：市价触及触发价后自动转为市价单 */
    public static Order stop(String symbol, Side side, double triggerPrice, int quantity, String traderName) {
        Order order = new Order(symbol, side, OrderType.STOP, triggerPrice, quantity, traderName);
        order.stopPrice = triggerPrice;
        return order;
    }

    /** 设置冰山单每层可见数量，返回自身便于链式调用 */
    public Order withIcebergSlice(int slice) {
        this.icebergSlice = slice;
        return this;
    }

    /** 当前对市场可见的数量（冰山单只露出一个切片，成交后自动补充下一层） */
    public int effectiveQuantity() {
        return icebergSlice < 0 ? quantity : Math.min(icebergSlice, quantity);
    }

    public void reduceQuantity(int amount) {
        this.quantity -= amount;
    }

    public long getOrderId() {
        return orderId;
    }

    public String getSymbol() {
        return symbol;
    }

    public Side getSide() {
        return side;
    }

    public OrderType getType() {
        return type;
    }

    public double getPrice() {
        return price;
    }

    public int getQuantity() {
        return quantity;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getTraderName() {
        return traderName;
    }

    public double getStopPrice() {
        return stopPrice;
    }

    public int getIcebergSlice() {
        return icebergSlice;
    }

    public boolean isStopTriggered() {
        return stopTriggered;
    }

    /** 标记该市价单由止损单触发而来（用于成交日志可解释性） */
    public void markStopTriggered() {
        this.stopTriggered = true;
    }

    @Override
    public String toString() {
        return "Order{id=" + orderId +
                ", trader=" + traderName +
                ", side=" + side +
                ", type=" + type +
                ", price=" + price +
                ", qty=" + quantity + "}";
    }

}
