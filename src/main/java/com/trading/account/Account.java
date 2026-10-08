package com.trading.account;

import com.trading.model.Side;

/**
 * 交易账户：现金 + 持仓 + 已实现盈亏 + 手续费。
 * 持仓为正表示多头，为负表示空头（做市商可能持有空头）；
 * 均价采用加权平均成本法，平仓时结转已实现盈亏。
 */
public class Account {

    private final String name;
    private double cash;
    private int position;        // 正 = 多头，负 = 空头
    private double avgCost;      // 持仓均价（加权平均成本）
    private double realizedPnl;  // 已实现盈亏
    private double totalFees;    // 累计手续费
    private int tradeCount;      // 累计成交笔数

    public Account(String name, double initialCash) {
        this.name = name;
        this.cash = initialCash;
    }

    /**
     * 结算一笔成交：买方按成交额 + 手续费扣现金，卖方按成交额 - 手续费收现金。
     */
    public void applyFill(Side side, double price, int quantity, double fee) {
        totalFees += fee;
        tradeCount++;
        if (side == Side.BUY) {
            buy(price, quantity);
            cash -= price * quantity + fee;
        } else {
            sell(price, quantity);
            cash += price * quantity - fee;
        }
    }

    private void buy(double price, int quantity) {
        if (position < 0) {
            // 先平空头
            int cover = Math.min(-position, quantity);
            realizedPnl += (avgCost - price) * cover;
            position += cover;
            if (position == 0) {
                avgCost = 0;
            }
            int rest = quantity - cover;
            if (rest > 0) {
                openPosition(price, rest);
            }
        } else {
            openPosition(price, quantity);
        }
    }

    private void sell(double price, int quantity) {
        if (position > 0) {
            // 先平多头
            int close = Math.min(position, quantity);
            realizedPnl += (price - avgCost) * close;
            position -= close;
            if (position == 0) {
                avgCost = 0;
            }
            int rest = quantity - close;
            if (rest > 0) {
                // 转为空头
                position = -rest;
                avgCost = price;
            }
        } else {
            // 加空头
            double shortSize = -position;
            avgCost = (avgCost * shortSize + price * quantity) / (shortSize + quantity);
            position -= quantity;
        }
    }

    private void openPosition(double price, int quantity) {
        double oldQuantity = position;
        avgCost = (avgCost * oldQuantity + price * quantity) / (oldQuantity + quantity);
        position += quantity;
    }

    /** 净值 = 现金 + 持仓市值（按市价标记） */
    public double equity(double markPrice) {
        return cash + position * markPrice;
    }

    /** 收益率 = (净值 - 初始资金) / 初始资金 */
    public double returnRate(double initialCash, double markPrice) {
        return (equity(markPrice) - initialCash) / initialCash;
    }

    public String getName() {
        return name;
    }

    public double getCash() {
        return cash;
    }

    public int getPosition() {
        return position;
    }

    public double getAvgCost() {
        return avgCost;
    }

    public double getRealizedPnl() {
        return realizedPnl;
    }

    public double getTotalFees() {
        return totalFees;
    }

    public int getTradeCount() {
        return tradeCount;
    }

}
