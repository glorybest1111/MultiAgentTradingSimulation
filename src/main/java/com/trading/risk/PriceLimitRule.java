package com.trading.risk;

import com.trading.model.Order;

/**
 * 涨跌停板规则：委托价格相对参考价偏离超过 ±limitPct 时拒单。
 *
 * <p>市价单不受价格限制约束（市价单本身没有委托价），但如果<b>最新成交价</b>
 * 已经触及停板，则一律拒单，模拟真实交易所「封板状态下的委托限制」。
 */
public class PriceLimitRule {

    private final double limitPct;

    public PriceLimitRule(double limitPct) {
        this.limitPct = limitPct;
    }

    public ValidationResult check(Order order, MarketContext context) {
        double reference = context.getReferencePrice();
        if (reference <= 0) {
            return ValidationResult.accept();
        }
        double upper = reference * (1 + limitPct);
        double lower = reference * (1 - limitPct);

        boolean boardLockedUp = context.getLastPrice() >= upper;
        boolean boardLockedDown = context.getLastPrice() <= lower;

        if (order.getPrice() > upper) {
            return ValidationResult.reject(String.format(
                    "price limit: %.2f exceeds upper board %.2f (+%.0f%%)",
                    order.getPrice(), upper, limitPct * 100));
        }
        if (order.getPrice() < lower) {
            return ValidationResult.reject(String.format(
                    "price limit: %.2f below lower board %.2f (-%.0f%%)",
                    order.getPrice(), lower, limitPct * 100));
        }
        if (boardLockedUp) {
            return ValidationResult.reject("limit up: market locked at upper board, order rejected");
        }
        if (boardLockedDown) {
            return ValidationResult.reject("limit down: market locked at lower board, order rejected");
        }
        return ValidationResult.accept();
    }

    public double getUpper(double referencePrice) {
        return referencePrice * (1 + limitPct);
    }

    public double getLower(double referencePrice) {
        return referencePrice * (1 - limitPct);
    }

    public double getLimitPct() {
        return limitPct;
    }
}
