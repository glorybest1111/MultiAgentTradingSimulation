package com.trading.agent;

import com.trading.market.PriceHistory;
import com.trading.model.Action;
import com.trading.model.Decision;
import com.trading.model.Order;
import com.trading.model.OrderType;
import com.trading.model.Side;

/**
 * AI 策略 2：价值投资者（Value Investor）。
 * 关注当前价格与历史价值（20 期均线）的偏离：
 * 价格低于均值 2% 以上视为低估买入，高于均值 2% 以上视为高估卖出。
 * 特点：交易频率低、逆向投资。
 */
public class ValueTrader implements TraderAgent {

    private static final int FAIR_SMA = 20;
    private static final double DEVIATION_THRESHOLD = 0.02;
    private static final int BASE_QUANTITY = 15;
    private static final int MAX_POSITION = 60;   // 仓位上限：防止单边行情下过度加仓

    @Override
    public String getName() {
        return "ValueTrader";
    }

    @Override
    public Decision onTick(MarketSnapshot snapshot, TradingApi api) {
        PriceHistory history = snapshot.getHistory();
        double fairValue = history.sma(FAIR_SMA);
        if (Double.isNaN(fairValue)) {
            return Decision.hold(getName(), "collecting price history");
        }

        double deviation = (snapshot.getPrice() - fairValue) / fairValue;

        if (deviation < -DEVIATION_THRESHOLD) {
            double confidence = Math.min(0.9, 0.5 + (-deviation - DEVIATION_THRESHOLD) * 20);
            int quantity = (int) Math.min(BASE_QUANTITY * (1 + confidence),
                    snapshot.getCash() / snapshot.getPrice());
            quantity = Math.min(quantity, Math.max(0, MAX_POSITION - snapshot.getPosition()));
            if (quantity <= 0) {
                return Decision.hold(getName(), "undervalued but position limit reached or insufficient cash");
            }
            api.placeOrder(new Order(snapshot.getSymbol(), Side.BUY, OrderType.MARKET,
                    snapshot.getPrice(), quantity, getName()));
            return Decision.trade(getName(), Action.BUY, quantity, confidence,
                    String.format("undervalued: price %.2f%% below 20-period average, betting on mean reversion",
                            -deviation * 100));
        }

        if (deviation > DEVIATION_THRESHOLD) {
            int quantity = Math.min(BASE_QUANTITY, Math.max(0, snapshot.getPosition()));
            if (quantity <= 0) {
                return Decision.hold(getName(), "overvalued but no position to sell");
            }
            api.placeOrder(new Order(snapshot.getSymbol(), Side.SELL, OrderType.MARKET,
                    snapshot.getPrice(), quantity, getName()));
            return Decision.trade(getName(), Action.SELL, quantity,
                    Math.min(0.9, 0.5 + (deviation - DEVIATION_THRESHOLD) * 20),
                    String.format("overvalued: price %.2f%% above 20-period average", deviation * 100));
        }

        return Decision.hold(getName(),
                String.format("price near fair value (deviation %+.2f%%)", deviation * 100));
    }

}
