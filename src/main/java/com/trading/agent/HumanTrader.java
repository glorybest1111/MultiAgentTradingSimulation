package com.trading.agent;

import com.trading.model.Action;
import com.trading.model.Decision;
import com.trading.model.Order;
import com.trading.model.OrderType;
import com.trading.model.Side;

/**
 * 人类交易者：把外部输入翻译成订单。
 *
 * <p>输入源已抽象为 {@link CommandSource}：控制台用 {@link ScannerCommandSource}，
 * GUI 可实现自己的非阻塞队列版本。本类不持有任何 Scanner，
 * 因此可以被 GUI 线程 / Timer 线程安全地驱动。
 *
 * <p>用法：java com.trading.Main --human
 */
public class HumanTrader implements TraderAgent {

    private final CommandSource commandSource;
    private boolean exited;

    public HumanTrader() {
        this(new ScannerCommandSource());
    }

    public HumanTrader(CommandSource commandSource) {
        this.commandSource = commandSource;
    }

    @Override
    public String getName() {
        return "HumanTrader";
    }

    /** 用户已选择退出（GUI/控制台都能用它判断是否需要停止驱动） */
    public boolean hasExited() {
        return exited;
    }

    @Override
    public Decision onTick(MarketSnapshot snapshot, TradingApi api) {
        TradeCommand command = commandSource.nextCommand(snapshot);
        if (command == null) {
            return Decision.hold(getName(), "manual decision: no input");
        }
        switch (command.getKind()) {
            case BUY: {
                int quantity = (int) Math.min(command.getQuantity(), snapshot.getCash() / snapshot.getPrice());
                if (quantity <= 0) {
                    return Decision.hold(getName(), "manual buy failed: insufficient cash");
                }
                api.placeOrder(new Order(snapshot.getSymbol(), Side.BUY, OrderType.MARKET,
                        snapshot.getPrice(), quantity, getName()));
                return Decision.trade(getName(), Action.BUY, quantity, 1.0,
                        String.format("manual decision: buy %d shares at market", quantity));
            }
            case SELL: {
                int quantity = Math.min(command.getQuantity(), Math.max(0, snapshot.getPosition()));
                if (quantity <= 0) {
                    return Decision.hold(getName(), "manual sell failed: no position");
                }
                api.placeOrder(new Order(snapshot.getSymbol(), Side.SELL, OrderType.MARKET,
                        snapshot.getPrice(), quantity, getName()));
                return Decision.trade(getName(), Action.SELL, quantity, 1.0,
                        String.format("manual decision: sell %d shares at market", quantity));
            }
            case EXIT:
                exited = true;
                return Decision.hold(getName(), "manual decision: exit requested");
            default:
                return Decision.hold(getName(), "manual decision: hold");
        }
    }
}
