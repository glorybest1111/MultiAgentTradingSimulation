package com.trading.agent;

import com.trading.market.PriceHistory;
import com.trading.model.Decision;
import com.trading.model.Order;
import com.trading.model.OrderType;
import com.trading.model.Side;

/**
 * 做市商（Market Maker）：
 * 持续在最新价两侧同时挂出买卖报价赚取价差（bid = price - spread, ask = price + spread），
 * 并根据库存倾斜报价位置——多头库存时压低报价鼓励卖出，空头库存时抬高报价鼓励买入。
 * 加入做市商后市场价差收窄、流动性提高、成交速度增加。
 */
public class MarketMakerAgent implements TraderAgent {

    private static final int QUOTE_SIZE = 20;
    private static final double MIN_HALF_SPREAD = 0.02;
    private static final double INVENTORY_SKEW = 0.002;

    private Order bidQuote;
    private Order askQuote;

    @Override
    public String getName() {
        return "MarketMaker";
    }

    @Override
    public Decision onTick(MarketSnapshot snapshot, TradingApi api) {
        // 撤掉上一期报价
        if (bidQuote != null) {
            api.cancelOrder(bidQuote);
        }
        if (askQuote != null) {
            api.cancelOrder(askQuote);
        }

        PriceHistory history = snapshot.getHistory();
        double volatility = history.volatility(20);
        if (Double.isNaN(volatility)) {
            volatility = 0.01;
        }
        double halfSpread = Math.max(MIN_HALF_SPREAD, volatility * 0.5 + 0.01);

        // 库存倾斜：多头压价、空头抬价，引导库存回归零
        double skew = snapshot.getPosition() * INVENTORY_SKEW;
        double bidPrice = Math.round((snapshot.getPrice() - halfSpread - skew) * 100) / 100.0;
        double askPrice = Math.round((snapshot.getPrice() + halfSpread - skew) * 100) / 100.0;

        bidQuote = api.placeOrder(new Order(snapshot.getSymbol(), Side.BUY, OrderType.LIMIT,
                bidPrice, QUOTE_SIZE, getName()));
        askQuote = api.placeOrder(new Order(snapshot.getSymbol(), Side.SELL, OrderType.LIMIT,
                askPrice, QUOTE_SIZE, getName()));

        return Decision.hold(getName(),
                String.format("quoting two-sided: bid %.2f / ask %.2f (half-spread %.2f, inventory %d)",
                        bidPrice, askPrice, halfSpread, snapshot.getPosition()));
    }

}
