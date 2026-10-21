package com.trading.agent;

/**
 * 人类交易指令：控制台 / GUI / 脚本三种输入源统一的数据结构。
 */
public class TradeCommand {

    public enum Kind {
        BUY, SELL, HOLD, EXIT
    }

    private final Kind kind;
    private final int quantity;

    private TradeCommand(Kind kind, int quantity) {
        this.kind = kind;
        this.quantity = Math.max(0, quantity);
    }

    public static TradeCommand buy(int quantity) {
        return new TradeCommand(Kind.BUY, quantity);
    }

    public static TradeCommand sell(int quantity) {
        return new TradeCommand(Kind.SELL, quantity);
    }

    public static TradeCommand hold() {
        return new TradeCommand(Kind.HOLD, 0);
    }

    public static TradeCommand exit() {
        return new TradeCommand(Kind.EXIT, 0);
    }

    public Kind getKind() {
        return kind;
    }

    public int getQuantity() {
        return quantity;
    }

    @Override
    public String toString() {
        return kind + (quantity > 0 ? " " + quantity : "");
    }
}
