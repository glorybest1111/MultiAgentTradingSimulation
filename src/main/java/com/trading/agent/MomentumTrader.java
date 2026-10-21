package com.trading.agent;

import com.trading.market.PriceHistory;
import com.trading.model.Action;
import com.trading.model.Decision;
import com.trading.model.Order;
import com.trading.model.OrderType;
import com.trading.model.Side;

/**
 * AI 策略 1：趋势交易者（Momentum Trader）。
 * 关注价格趋势：短均线上穿长均线且动量为正时买入，下穿时卖出。
 * 买入同时挂出止损单（跌破成本 3% 自动市价卖出），演示 Stop-Loss 高级订单的风险控制价值。
 *
 * <p>情绪/新闻的作用（{@link MarketSnapshot#getSentimentBias()}）：
 * <ul>
 *   <li>顺情绪加仓：乐观情绪上调置信度并放大下单量</li>
 *   <li>逆情绪风控：情绪极度悲观时（bias &lt; -0.6）拒绝追多，避免「接飞刀」</li>
 * </ul>
 * 情绪关闭（返回 0）时行为与本策略的纯技术面版本完全一致。
 *
 * <p>特点：交易频率较高、风险较高（但有止损保护）。
 */
public class MomentumTrader implements TraderAgent {

    private static final int SHORT_SMA = 5;
    private static final int LONG_SMA = 15;
    private static final int BASE_QUANTITY = 10;
    private static final double STOP_DISTANCE = 0.03;   // 止损距离 3%
    private static final double PANIC_MOOD = -0.6;      // 极端悲观情绪阈值

    private Order stopOrder;   // 当前持仓的保护性止损单

    @Override
    public String getName() {
        return "MomentumTrader";
    }

    @Override
    public Decision onTick(MarketSnapshot snapshot, TradingApi api) {
        PriceHistory history = snapshot.getHistory();
        if (history.size() < LONG_SMA + 1) {
            return Decision.hold(getName(), "collecting price history");
        }

        double shortSma = history.sma(SHORT_SMA);
        double longSma = history.sma(LONG_SMA);
        double momentum = history.momentum(10);
        double bias = snapshot.getSentimentBias();

        if (shortSma > longSma && momentum > 0.002) {
            // 逆情绪风控：趋势向上但情绪极度悲观时观望
            if (bias < PANIC_MOOD) {
                return Decision.hold(getName(), String.format(
                        "uptrend ignored: sentiment %.2f below %.2f (news too bearish to chase)", bias, PANIC_MOOD));
            }
            double confidence = clamp(Math.min(0.9, 0.55 + momentum * 10) + 0.15 * bias);
            int quantity = (int) (BASE_QUANTITY * (1 + confidence) * (1 + 0.3 * bias));
            quantity = (int) Math.min(quantity, snapshot.getCash() / snapshot.getPrice());
            if (quantity <= 0) {
                return Decision.hold(getName(), "uptrend but insufficient cash");
            }
            api.placeOrder(new Order(snapshot.getSymbol(), Side.BUY, OrderType.MARKET,
                    snapshot.getPrice(), quantity, getName()));
            // 更新保护性止损单：跌破成本 3% 自动市价卖出
            if (stopOrder != null) {
                api.cancelOrder(stopOrder);
            }
            double trigger = Math.round(snapshot.getPrice() * (1 - STOP_DISTANCE) * 100) / 100.0;
            stopOrder = api.placeOrder(Order.stop(snapshot.getSymbol(), Side.SELL,
                    trigger, Math.max(1, snapshot.getPosition() + quantity), getName()));
            return Decision.trade(getName(), Action.BUY, quantity, confidence,
                    String.format("uptrend: short SMA %.2f > long SMA %.2f, momentum +%.2f%%"
                                    + ", sentiment %+.2f (stop-loss placed at %.2f)",
                            shortSma, longSma, momentum * 100, bias, trigger));
        }

        if (shortSma < longSma) {
            int quantity = Math.min(BASE_QUANTITY, Math.max(0, snapshot.getPosition()));
            if (quantity <= 0) {
                return Decision.hold(getName(), "downtrend but no position to sell");
            }
            if (stopOrder != null) {
                api.cancelOrder(stopOrder);
                stopOrder = null;
            }
            api.placeOrder(new Order(snapshot.getSymbol(), Side.SELL, OrderType.MARKET,
                    snapshot.getPrice(), quantity, getName()));
            return Decision.trade(getName(), Action.SELL, quantity, clamp(0.55 + 0.15 * bias),
                    String.format("downtrend: short SMA %.2f < long SMA %.2f, sentiment %+.2f (protective stop cancelled)",
                            shortSma, longSma, bias));
        }

        return Decision.hold(getName(), "no clear trend signal");
    }

    private static double clamp(double value) {
        return Math.max(0.05, Math.min(0.95, value));
    }
}
