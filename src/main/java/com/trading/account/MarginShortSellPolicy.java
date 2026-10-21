package com.trading.account;

import com.trading.engine.ShortSellPolicy;
import com.trading.risk.ValidationResult;

import java.util.Map;

/**
 * 基于账户的做空策略实现：按 {@code SimulationConfig} 指定的模式校验并在成交后记账。
 *
 * <p>MARGIN 模式下的规则：
 * <ul>
 *   <li>卖单中超出当前多头持仓的部分，视为新开/加大空头</li>
 *   <li>需冻结 {@code 数量 × 价格 × 初始保证金率} 的现金；不足则拒单</li>
 *   <li>成交后记录借券数量（borrowedShares）与占用保证金（margin）</li>
 * </ul>
 */
public class MarginShortSellPolicy implements ShortSellPolicy {

    private final Map<String, Account> accounts;
    private final ShortSellingMode mode;
    private final double initialMarginRate;
    private final double maintenanceMarginRate;
    private int rejectedCount;

    public MarginShortSellPolicy(Map<String, Account> accounts, ShortSellingMode mode,
                                 double initialMarginRate, double maintenanceMarginRate) {
        this.accounts = accounts;
        this.mode = mode == null ? ShortSellingMode.LEGACY : mode;
        this.initialMarginRate = initialMarginRate;
        this.maintenanceMarginRate = maintenanceMarginRate;
    }

    @Override
    public ValidationResult checkSell(String traderName, String symbol, double price, int quantity) {
        if (mode == ShortSellingMode.LEGACY) {
            return ValidationResult.accept();
        }
        Account account = accounts.get(traderName);
        if (account == null) {
            // 虚拟流动性池等无账户对手方不参与做空限制
            return ValidationResult.accept();
        }
        int currentPosition = account.getPosition();
        int increment = quantity - Math.max(0, currentPosition);
        if (increment <= 0) {
            return ValidationResult.accept();   // 卖出的是已有多头，属于平仓
        }
        if (mode == ShortSellingMode.DISABLED) {
            rejectedCount++;
            return ValidationResult.reject(String.format(
                    "short selling disabled: would create %d net short shares", increment));
        }
        double required = increment * price * initialMarginRate;
        if (account.getCash() < required) {
            rejectedCount++;
            return ValidationResult.reject(String.format(
                    "insufficient margin: need %.2f for %d short shares @ %.2f (%.0f%% rate)",
                    required, increment, price, initialMarginRate * 100));
        }
        return ValidationResult.accept();
    }

    @Override
    public void onSellFill(String traderName, String symbol, double price, int quantity) {
        if (mode == ShortSellingMode.LEGACY) {
            return;
        }
        Account account = accounts.get(traderName);
        if (account == null) {
            return;
        }
        int increment = Math.min(quantity, Math.max(0, -account.getPosition()));
        if (increment <= 0) {
            return;
        }
        account.reserveMargin(price, increment);
    }

    public int getRejectedCount() {
        return rejectedCount;
    }

    public ShortSellingMode getMode() {
        return mode;
    }

    public double getMaintenanceMarginRate() {
        return maintenanceMarginRate;
    }
}
