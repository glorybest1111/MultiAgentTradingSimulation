package com.trading.agent;

import com.trading.ai.DebateSystem;
import com.trading.model.Action;
import com.trading.model.Decision;
import com.trading.model.Order;
import com.trading.model.OrderType;
import com.trading.model.Side;

/**
 * 多 Agent 辩论交易者：Bull 与 Bear 各自陈述观点，Judge 仲裁后执行交易。
 * 辩论过程本身作为理由记录在决策日志中（可解释 AI）。
 */
public class DebateTrader implements TraderAgent {

    private final DebateSystem debateSystem = new DebateSystem();

    @Override
    public String getName() {
        return "DebateTrader";
    }

    @Override
    public Decision onTick(MarketSnapshot snapshot, TradingApi api) {
        Decision decision = debateSystem.debate(snapshot);
        if (decision.getAction() == Action.HOLD) {
            return Decision.hold(getName(), decision.getReason());
        }

        Side side = decision.getAction() == Action.BUY ? Side.BUY : Side.SELL;
        int quantity = decision.getQuantity();
        if (side == Side.BUY) {
            quantity = (int) Math.min(quantity, snapshot.getCash() / snapshot.getPrice());
        } else {
            quantity = Math.min(quantity, Math.max(0, snapshot.getPosition()));
        }
        if (quantity <= 0) {
            return Decision.hold(getName(), decision.getReason() + " (but no cash/position to act)");
        }

        api.placeOrder(new Order(snapshot.getSymbol(), side, OrderType.MARKET,
                snapshot.getPrice(), quantity, getName()));
        return Decision.trade(getName(), decision.getAction(), quantity,
                decision.getConfidence(), decision.getReason());
    }

}
