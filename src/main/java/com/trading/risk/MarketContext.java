package com.trading.risk;

/**
 * 风控所需的最小市场上下文。
 *
 * <p>刻意做成一个只读的值对象：撮合引擎在提交订单前把必要的价格/时间信息打包传给他，
 * 风控规则因此不需要持有 {@code MarketSimulation} 的引用，避免层与层之间互相纠缠。
 */
public class MarketContext {

    private final String symbol;
    private final double lastPrice;
    private final double referencePrice;   // 参考价（一般是初始价/昨收），用于计算涨跌停
    private final int tick;
    private final long timestampMillis;

    public MarketContext(String symbol, double lastPrice, double referencePrice, int tick, long timestampMillis) {
        this.symbol = symbol;
        this.lastPrice = lastPrice;
        this.referencePrice = referencePrice;
        this.tick = tick;
        this.timestampMillis = timestampMillis;
    }

    public String getSymbol() {
        return symbol;
    }

    public double getLastPrice() {
        return lastPrice;
    }

    public double getReferencePrice() {
        return referencePrice;
    }

    public int getTick() {
        return tick;
    }

    public long getTimestampMillis() {
        return timestampMillis;
    }
}
