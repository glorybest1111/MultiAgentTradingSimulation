package com.trading.agent;

import com.trading.market.PriceHistory;
import com.trading.model.Action;
import com.trading.model.Decision;
import com.trading.model.Order;
import com.trading.model.OrderType;
import com.trading.model.Side;

/**
 * AI 策略 3：高频交易者（High Frequency Trader）。
 * 短线均值回归抢帽子：价格偏离 3 期均线超过 1.5 倍波动率时反向操作。
 * 特点：单笔数量小、交易频率最高，赚取微小价格波动。
 */
public class HighFrequencyTrader implements TraderAgent {

    private static final int MEAN_SMA = 3;
    private static final double Z_THRESHOLD = 1.0;
    private static final int SCALP_QUANTITY = 5;

    @Override
    public String getName() {
        return "HFTrader";
    }

    @Override
    public Decision onTick(MarketSnapshot snapshot, TradingApi api) {
        PriceHistory history = snapshot.getHistory();
        double mean = history.sma(MEAN_SMA);
        double volatility = history.volatility(10);
        if (Double.isNaN(mean) || Double.isNaN(volatility)) {
            return Decision.hold(getName(), "collecting price history");
        }

        double zScore = (snapshot.getPrice() - mean) / Math.max(volatility * mean, 0.001);

        if (zScore < -Z_THRESHOLD) {
            int quantity = (int) Math.min(SCALP_QUANTITY, snapshot.getCash() / snapshot.getPrice());
            if (quantity <= 0) {
                return Decision.hold(getName(), "dip signal but insufficient cash");
            }
            api.placeOrder(new Order(snapshot.getSymbol(), Side.BUY, OrderType.MARKET,
                    snapshot.getPrice(), quantity, getName()));
            return Decision.trade(getName(), Action.BUY, quantity, 0.6,
                    String.format("short-term dip: price %.2f below 3-period mean by %.2f sigma, scalping the bounce",
                            snapshot.getPrice() - mean, -zScore));
        }

        if (zScore > Z_THRESHOLD) {
            int quantity = Math.min(SCALP_QUANTITY, Math.max(0, snapshot.getPosition()));
            if (quantity <= 0) {
                return Decision.hold(getName(), "spike signal but no position to sell");
            }
            api.placeOrder(new Order(snapshot.getSymbol(), Side.SELL, OrderType.MARKET,
                    snapshot.getPrice(), quantity, getName()));
            return Decision.trade(getName(), Action.SELL, quantity, 0.6,
                    String.format("short-term spike: price %.2f above 3-period mean by %.2f sigma, taking profit",
                            snapshot.getPrice() - mean, zScore));
        }

        return Decision.hold(getName(), "no scalping signal");
    }

}
