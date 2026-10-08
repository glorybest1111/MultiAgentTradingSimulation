package com.trading.risk;

/**
 * 手续费计算器：按成交金额比例双边收取，设最低手续费。
 * 示例：交易金额 10000 美元，费率 0.1%，手续费 = 10000 × 0.001 = 10 美元。
 */
public class FeeCalculator {

    private static final double MIN_FEE = 0.01;

    private final double rate;

    public FeeCalculator(double rate) {
        this.rate = rate;
    }

    /** 单笔手续费 */
    public double fee(double tradeValue) {
        return Math.max(MIN_FEE, tradeValue * rate);
    }

    /** 买入总成本 = 成交金额 + 手续费 */
    public double buyCost(double price, int quantity) {
        double value = price * quantity;
        return value + fee(value);
    }

    /** 卖出净收入 = 成交金额 - 手续费 */
    public double sellProceeds(double price, int quantity) {
        double value = price * quantity;
        return value - fee(value);
    }

    public double getRate() {
        return rate;
    }

}
