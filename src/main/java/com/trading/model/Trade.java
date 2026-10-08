package com.trading.model;

import java.time.LocalDateTime;

/**
 * 成交记录：一次撮合的结果，附带成交原因（用于 AI 可解释性）。
 */
public class Trade {

    private final String symbol;
    private final Order buyOrder;
    private final Order sellOrder;
    private final double price;
    private final int quantity;
    private final String reason;
    private final LocalDateTime timestamp;

    public Trade(Order buyOrder, Order sellOrder, double price, int quantity, String reason) {
        this.symbol = buyOrder.getSymbol();
        this.buyOrder = buyOrder;
        this.sellOrder = sellOrder;
        this.price = price;
        this.quantity = quantity;
        this.reason = reason;
        this.timestamp = LocalDateTime.now();
    }

    public String getSymbol() {
        return symbol;
    }

    public Order getBuyOrder() {
        return buyOrder;
    }

    public Order getSellOrder() {
        return sellOrder;
    }

    public double getPrice() {
        return price;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getBuyTrader() {
        return buyOrder.getTraderName();
    }

    public String getSellTrader() {
        return sellOrder.getTraderName();
    }

    @Override
    public String toString() {
        return "Trade{symbol='" + symbol + "', price=" + price +
                ", qty=" + quantity +
                ", buyer=" + getBuyTrader() +
                ", seller=" + getSellTrader() +
                ", reason='" + reason + "'}";
    }

}
