package com.trading.agent;

import com.trading.model.Action;
import com.trading.model.Decision;
import com.trading.model.Order;
import com.trading.model.OrderType;
import com.trading.model.Side;

import java.util.Scanner;

/**
 * 人类交易者：控制台交互式下单。
 * 用法：java com.trading.Main --human
 * 每期输入指令，例如：BUY 10 / SELL 5 / HOLD
 */
public class HumanTrader implements TraderAgent {

    private final Scanner scanner = new Scanner(System.in);

    @Override
    public String getName() {
        return "HumanTrader";
    }

    @Override
    public Decision onTick(MarketSnapshot snapshot, TradingApi api) {
        System.out.printf("[第 %d 期] %s @ %.2f | 现金 %.2f | 持仓 %d (均价 %.2f) | 情绪 %+.2f%n",
                snapshot.getTick(), snapshot.getSymbol(), snapshot.getPrice(),
                snapshot.getCash(), snapshot.getPosition(), snapshot.getAvgCost(),
                snapshot.getMarketMood());
        if (snapshot.getLatestNews() != null) {
            System.out.println("  新闻: " + snapshot.getLatestNews());
        }
        System.out.print("  指令 (BUY/SELL/HOLD [数量], 如 BUY 10): ");

        String line = scanner.hasNextLine() ? scanner.nextLine().trim() : "HOLD";
        String[] parts = line.split("\\s+");
        String command = parts[0].toUpperCase();
        int quantity = parts.length > 1 ? parseQuantity(parts[1]) : 10;

        switch (command) {
            case "BUY":
                quantity = (int) Math.min(quantity, snapshot.getCash() / snapshot.getPrice());
                if (quantity <= 0) {
                    return Decision.hold(getName(), "manual buy failed: insufficient cash");
                }
                api.placeOrder(new Order(snapshot.getSymbol(), Side.BUY, OrderType.MARKET,
                        snapshot.getPrice(), quantity, getName()));
                return Decision.trade(getName(), Action.BUY, quantity, 1.0,
                        String.format("manual decision: buy %d shares at market", quantity));
            case "SELL":
                quantity = Math.min(quantity, Math.max(0, snapshot.getPosition()));
                if (quantity <= 0) {
                    return Decision.hold(getName(), "manual sell failed: no position");
                }
                api.placeOrder(new Order(snapshot.getSymbol(), Side.SELL, OrderType.MARKET,
                        snapshot.getPrice(), quantity, getName()));
                return Decision.trade(getName(), Action.SELL, quantity, 1.0,
                        String.format("manual decision: sell %d shares at market", quantity));
            default:
                return Decision.hold(getName(), "manual decision: hold");
        }
    }

    private int parseQuantity(String text) {
        try {
            return Math.max(1, Integer.parseInt(text));
        } catch (NumberFormatException e) {
            return 10;
        }
    }

}
