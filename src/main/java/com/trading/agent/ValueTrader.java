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
 *
 * <p>情绪/新闻的作用（{@link MarketSnapshot#getSentimentBias()}）：
 * 把「低估/高估」的判定线做成情绪自适应——情绪乐观时基本面利好可信，
 * 允许在更接近均线时买入（阈值放宽最多 1%）；情绪悲观时判定线右移，
 * 必须跌得更深才认为是便宜（避免在坏消息趋势中过早抄底）。
 *
 * <p>特点：交易频率低、逆向投资。
 */
public class ValueTrader implements TraderAgent {

    private static final int FAIR_SMA = 20;
    private static final double DEVIATION_THRESHOLD = 0.02;
    private static final double SENTIMENT_TILT = 0.01;   // 情绪最多可使阈值平移 1%
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
        double bias = snapshot.getSentimentBias();
        // 情绪乐观 → 阈值放宽（更容易判定为低估）；情绪悲观 → 阈值收紧
        double effectiveDeviation = deviation - bias * SENTIMENT_TILT;

        if (effectiveDeviation < -DEVIATION_THRESHOLD) {
            double confidence = Math.min(0.9, 0.5 + (-effectiveDeviation - DEVIATION_THRESHOLD) * 20);
            int quantity = (int) Math.min(BASE_QUANTITY * (1 + confidence),
                    snapshot.getCash() / snapshot.getPrice());
            quantity = Math.min(quantity, Math.max(0, MAX_POSITION - snapshot.getPosition()));
            if (quantity <= 0) {
                return Decision.hold(getName(), "undervalued but position limit reached or insufficient cash");
            }
            api.placeOrder(new Order(snapshot.getSymbol(), Side.BUY, OrderType.MARKET,
                    snapshot.getPrice(), quantity, getName()));
            return Decision.trade(getName(), Action.BUY, quantity, confidence,
                    String.format("undervalued: price %.2f%% below 20-period average"
                                    + " (sentiment %+.2f tilts threshold by %.2f%%), betting on mean reversion",
                            -deviation * 100, bias, bias * SENTIMENT_TILT * 100));
        }

        if (effectiveDeviation > DEVIATION_THRESHOLD) {
            int quantity = Math.min(BASE_QUANTITY, Math.max(0, snapshot.getPosition()));
            if (quantity <= 0) {
                return Decision.hold(getName(), "overvalued but no position to sell");
            }
            api.placeOrder(new Order(snapshot.getSymbol(), Side.SELL, OrderType.MARKET,
                    snapshot.getPrice(), quantity, getName()));
            return Decision.trade(getName(), Action.SELL, quantity,
                    Math.min(0.9, 0.5 + (effectiveDeviation - DEVIATION_THRESHOLD) * 20),
                    String.format("overvalued: price %.2f%% above 20-period average (sentiment %+.2f), taking profit",
                            deviation * 100, bias));
        }

        return Decision.hold(getName(),
                String.format("price near fair value (deviation %+.2f%%, sentiment-adjusted %+.2f%%)",
                        deviation * 100, effectiveDeviation * 100));
    }
}
